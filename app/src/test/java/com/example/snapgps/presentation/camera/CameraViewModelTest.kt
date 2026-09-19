package com.example.snapgps.presentation.camera

import app.cash.turbine.test
import com.example.snapgps.domain.Clock
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.CaptureState
import com.example.snapgps.domain.model.FlashMode
import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.LocationStatus
import com.example.snapgps.domain.model.LowAccuracyBehavior
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private val now = 1_000_000_000L
    private val camera = FakeCameraRepository()
    private val location = FakeLocationRepository()
    private val settings = FakeSettingsRepository()
    private val processor = FakePhotoProcessor()
    private val photos = FakePhotoRepository()

    private fun fix(accuracy: Float = 6f, ageMs: Long = 1_000) =
        GpsLocation(25.3176, 82.9739, 81.0, accuracy, 0f, null, now - ageMs)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(
        appSettings: AppSettings = AppSettings(),
        geocoder: FakeGeocodingRepository = FakeGeocodingRepository()
    ): CameraViewModel {
        settings.state.value = appSettings
        val vm = CameraViewModel(
            cameraRepository = camera,
            locationRepository = location,
            geocodingRepository = geocoder,
            headingRepository = FakeHeadingRepository(),
            mapSnapshotRepository = FakeMapSnapshotRepository(),
            settingsRepository = settings,
            photoProcessor = processor,
            photoRepository = photos,
            // Advances with virtual time so throttling can be tested.
            clock = Clock { now + testScheduler.currentTime }
        )
        // Keep the state pipeline hot, as the screen would.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onPermissionsChanged(cameraGranted = true)
        return vm
    }

    @Test
    fun `capture with a good fix stamps location and returns to idle`() = runTest {
        location.updates.emit(fix())
        val vm = createViewModel()

        vm.events.test {
            vm.onCaptureClicked()
            val event = awaitItem() as CameraEvent.PhotoSaved
            assertNull(event.note)
            assertEquals(25.3176, event.photo.latitude!!, 0.0)
        }
        assertEquals(1, photos.saved.size)
        assertEquals(82.9739, photos.saved.single().longitude!!, 0.0)
        assertEquals("Varanasi, Uttar Pradesh", photos.saved.single().address)
        assertEquals(CaptureState.Idle, vm.uiState.value.captureState)
    }

    @Test
    fun `second tap while capturing is ignored`() = runTest {
        location.updates.emit(fix())
        val gate = CompletableDeferred<Unit>()
        camera.gate = gate
        val vm = createViewModel()

        vm.onCaptureClicked()
        assertEquals(CaptureState.Capturing, vm.uiState.value.captureState)
        vm.onCaptureClicked()
        vm.onCaptureClicked()
        gate.complete(Unit)

        assertEquals(1, camera.takePictureCalls)
        assertEquals(1, photos.saved.size)
        assertEquals(CaptureState.Idle, vm.uiState.value.captureState)
    }

    @Test
    fun `stale fix triggers a fresh location request`() = runTest {
        location.updates.emit(fix(ageMs = 120_000))
        location.currentLocation = fix(accuracy = 4f, ageMs = 0).copy(latitude = 26.0)
        val vm = createViewModel()

        vm.onCaptureClicked()

        assertEquals(1, location.currentLocationCalls)
        assertEquals(26.0, photos.saved.single().latitude!!, 0.0)
    }

    @Test
    fun `stale fix with no fresh fix saves without location`() = runTest {
        location.updates.emit(fix(ageMs = 120_000))
        location.currentLocation = null
        val vm = createViewModel()

        vm.events.test {
            vm.onCaptureClicked()
            val event = awaitItem() as CameraEvent.PhotoSaved
            assertTrue(event.note!!.contains("no GPS fix"))
        }
        assertNull(photos.saved.single().latitude)
    }

    @Test
    fun `no location permission still captures without GPS data`() = runTest {
        location.hasPermission = false
        val vm = createViewModel()

        assertEquals(LocationStatus.PermissionRequired, vm.uiState.value.locationStatus)
        vm.events.test {
            vm.onCaptureClicked()
            val event = awaitItem() as CameraEvent.PhotoSaved
            assertTrue(event.note!!.contains("permission"))
        }
        assertNull(photos.saved.single().latitude)
    }

    @Test
    fun `low accuracy stamps with a warning by default`() = runTest {
        location.updates.emit(fix(accuracy = 85f))
        val vm = createViewModel()

        assertEquals(LocationStatus.LowAccuracy(85f), vm.uiState.value.locationStatus)
        vm.events.test {
            vm.onCaptureClicked()
            val event = awaitItem() as CameraEvent.PhotoSaved
            assertTrue(event.note!!.contains("low GPS accuracy"))
        }
        assertEquals(85f, photos.saved.single().accuracy!!, 0f)
    }

    @Test
    fun `wait-for-accuracy blocks capture on a poor fix`() = runTest {
        location.updates.emit(fix(accuracy = 85f))
        val vm = createViewModel(AppSettings(lowAccuracyBehavior = LowAccuracyBehavior.WAIT_FOR_ACCURACY))

        vm.events.test {
            vm.onCaptureClicked()
            assertTrue(awaitItem() is CameraEvent.Message)
        }
        assertEquals(0, camera.takePictureCalls)
    }

    @Test
    fun `capture-without-stamp drops location on a poor fix`() = runTest {
        location.updates.emit(fix(accuracy = 85f))
        val vm = createViewModel(AppSettings(lowAccuracyBehavior = LowAccuracyBehavior.CAPTURE_WITHOUT_STAMP))

        vm.onCaptureClicked()

        assertNull(photos.saved.single().latitude)
    }

    @Test
    fun `location is not kept when neither stamped nor embedded`() = runTest {
        location.updates.emit(fix())
        val vm = createViewModel(AppSettings(stampLocationOnPhoto = false, embedGpsMetadata = false))

        vm.onCaptureClicked()

        assertNull(photos.saved.single().latitude)
        assertNull(photos.saved.single().address)
    }

    @Test
    fun `camera failure reports an error and returns to idle`() = runTest {
        location.updates.emit(fix())
        camera.failWith = IOException("boom")
        val vm = createViewModel()

        vm.events.test {
            vm.onCaptureClicked()
            assertTrue(awaitItem() is CameraEvent.CaptureFailed)
        }
        assertEquals(0, photos.saved.size)
        assertEquals(CaptureState.Idle, vm.uiState.value.captureState)
    }

    @Test
    fun `status is ready with a good fix and location off when disabled`() = runTest {
        location.updates.emit(fix(accuracy = 7f))
        val vm = createViewModel()
        assertEquals(LocationStatus.Ready(7f), vm.uiState.value.locationStatus)

        location.enabled.value = false
        assertEquals(LocationStatus.Disabled, vm.uiState.first { it.locationStatus == LocationStatus.Disabled }.locationStatus)
    }

    @Test
    fun `address follows a fix that arrives inside the throttle window`() = runTest {
        val geocoder = FakeGeocodingRepository { lat, _ -> if (lat > 28) "Stale place" else "Varanasi" }
        location.updates.emit(fix().copy(latitude = 29.2))
        val vm = createViewModel(geocoder = geocoder)
        assertEquals("Stale place", vm.uiState.value.address)

        advanceTimeBy(3_000)
        location.updates.emit(fix())
        runCurrent()
        assertEquals("Stale place", vm.uiState.value.address)

        // No further fixes arrive; the postponed lookup must still happen.
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals("Varanasi", vm.uiState.value.address)
    }

    @Test
    fun `address updates while fixes stream in every second`() = runTest {
        val geocoder = FakeGeocodingRepository { lat, _ -> if (lat > 28) "Stale place" else "Varanasi" }
        location.updates.emit(fix().copy(latitude = 29.2))
        val vm = createViewModel(geocoder = geocoder)
        assertEquals("Stale place", vm.uiState.value.address)

        // Moved, and the device keeps reporting a fresh fix every second.
        repeat(12) {
            advanceTimeBy(1_000)
            location.updates.emit(fix().copy(timestamp = now + testScheduler.currentTime))
        }
        runCurrent()
        assertEquals("Varanasi", vm.uiState.value.address)
    }

    @Test
    fun `failed lookup is retried without new fixes`() = runTest {
        var calls = 0
        val geocoder = FakeGeocodingRepository { _, _ -> if (++calls == 1) null else "Varanasi" }
        location.updates.emit(fix())
        val vm = createViewModel(geocoder = geocoder)
        assertNull(vm.uiState.value.address)

        advanceTimeBy(31_000)
        runCurrent()
        assertEquals("Varanasi", vm.uiState.value.address)
    }

    @Test
    fun `flash cycles off auto on`() = runTest {
        val vm = createViewModel()
        vm.onToggleFlash()
        assertEquals(FlashMode.AUTO, camera.lastFlashMode)
        vm.onToggleFlash()
        assertEquals(FlashMode.ON, camera.lastFlashMode)
        vm.onToggleFlash()
        assertEquals(FlashMode.OFF, vm.uiState.value.flashMode)
    }
}
