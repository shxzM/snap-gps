package com.example.snapgps.presentation.camera

import android.graphics.Bitmap
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.FlashMode
import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.LensFacing
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.domain.model.ZoomInfo
import com.example.snapgps.domain.repository.CameraRepository
import com.example.snapgps.domain.repository.DeleteResult
import com.example.snapgps.domain.repository.GeocodingRepository
import com.example.snapgps.domain.repository.HeadingRepository
import com.example.snapgps.domain.repository.LocationRepository
import com.example.snapgps.domain.repository.MapSnapshotRepository
import com.example.snapgps.domain.repository.PhotoProcessor
import com.example.snapgps.domain.repository.PhotoRepository
import com.example.snapgps.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.io.File

class FakeCameraRepository : CameraRepository {
    override val surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    override val zoom = MutableStateFlow(ZoomInfo())
    override val hasFlashUnit = MutableStateFlow(true)

    var takePictureCalls = 0
    /** When set, takePicture suspends until completed — simulates a slow shutter. */
    var gate: CompletableDeferred<Unit>? = null
    var failWith: Exception? = null
    var lastFlashMode: FlashMode = FlashMode.OFF

    override suspend fun availableLenses() = setOf(LensFacing.BACK, LensFacing.FRONT)
    override suspend fun bind(lifecycleOwner: LifecycleOwner, lens: LensFacing, flashMode: FlashMode) = awaitCancellation()
    override fun setFlashMode(mode: FlashMode) { lastFlashMode = mode }
    override fun setZoomRatio(ratio: Float) = Unit
    override fun focusAt(surfaceX: Float, surfaceY: Float) = Unit
    override fun setTargetRotation(rotation: Int) = Unit

    override suspend fun takePicture(): File {
        takePictureCalls++
        gate?.await()
        failWith?.let { throw it }
        return File.createTempFile("raw", ".jpg")
    }
}

class FakeLocationRepository : LocationRepository {
    val updates = MutableSharedFlow<GpsLocation>(replay = 1)
    val enabled = MutableStateFlow(true)
    var hasPermission = true
    var precise = true
    var currentLocation: GpsLocation? = null
    var currentLocationCalls = 0

    override fun locationUpdates(): Flow<GpsLocation> = updates
    override suspend fun getCurrentLocation(): GpsLocation? {
        currentLocationCalls++
        return currentLocation
    }
    override fun hasLocationPermission() = hasPermission
    override fun hasPreciseLocationPermission() = precise
    override fun locationEnabled(): Flow<Boolean> = enabled
}

class FakeGeocodingRepository(
    private val lookup: (Double, Double) -> String? = { _, _ -> "Varanasi, Uttar Pradesh" }
) : GeocodingRepository {
    override suspend fun getAddress(latitude: Double, longitude: Double) = lookup(latitude, longitude)
}

class FakeHeadingRepository : HeadingRepository {
    override fun headingUpdates(): Flow<Float> = emptyFlow()
    override fun declination(latitude: Double, longitude: Double, altitudeM: Double, timeMs: Long) = 0f
}

/** Bitmaps can't be created in JVM tests; records requests and reports the map as unavailable. */
class FakeMapSnapshotRepository : MapSnapshotRepository {
    val requests = mutableListOf<Pair<Double, Double>>()
    override suspend fun snapshot(latitude: Double, longitude: Double): Bitmap? {
        requests += latitude to longitude
        return null
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

class FakePhotoProcessor : PhotoProcessor {
    val processed = mutableListOf<PhotoMetadata>()
    override suspend fun process(source: File, metadata: PhotoMetadata, settings: AppSettings): File {
        processed += metadata
        return File.createTempFile("final", ".jpg")
    }
}

class FakePhotoRepository : PhotoRepository {
    private val photos = MutableStateFlow<List<Photo>>(emptyList())
    val saved = mutableListOf<PhotoMetadata>()

    override fun observePhotos(): StateFlow<List<Photo>> = photos
    override fun observePhoto(id: Long): Flow<Photo?> = photos.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun save(processedFile: File, metadata: PhotoMetadata): Photo {
        saved += metadata
        processedFile.delete()
        val photo = Photo(
            id = saved.size.toLong(),
            uri = "content://media/external/images/media/${saved.size}",
            latitude = metadata.latitude,
            longitude = metadata.longitude,
            altitude = metadata.altitude,
            accuracy = metadata.accuracy,
            speed = metadata.speed,
            bearing = metadata.bearing,
            address = metadata.address,
            capturedAt = metadata.dateTime.toEpochMilli()
        )
        photos.value = listOf(photo) + photos.value
        return photo
    }

    override suspend fun delete(photo: Photo): DeleteResult = DeleteResult.Deleted
    override suspend fun removeRecord(id: Long) = Unit
    override suspend fun pruneMissing() = Unit
}
