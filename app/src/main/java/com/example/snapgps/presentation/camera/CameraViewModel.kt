package com.example.snapgps.presentation.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapgps.domain.Clock
import com.example.snapgps.domain.format.GeoMath
import com.example.snapgps.domain.format.HeadingResolver
import com.example.snapgps.domain.format.LocationEvaluator
import com.example.snapgps.domain.format.LocationQuality
import com.example.snapgps.domain.format.OverlayContentBuilder
import com.example.snapgps.domain.format.PhotoMetadataFactory
import com.example.snapgps.domain.format.UnitConverter
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.CaptureState
import com.example.snapgps.domain.model.FlashMode
import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.LensFacing
import com.example.snapgps.domain.model.LowAccuracyBehavior
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.model.ZoomInfo
import com.example.snapgps.domain.repository.CameraRepository
import com.example.snapgps.domain.repository.GeocodingRepository
import com.example.snapgps.domain.repository.HeadingRepository
import com.example.snapgps.domain.repository.LocationRepository
import com.example.snapgps.domain.repository.MapSnapshotRepository
import com.example.snapgps.domain.repository.PhotoProcessor
import com.example.snapgps.domain.repository.PhotoRepository
import com.example.snapgps.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(
    private val cameraRepository: CameraRepository,
    private val locationRepository: LocationRepository,
    private val geocodingRepository: GeocodingRepository,
    private val headingRepository: HeadingRepository,
    private val mapSnapshotRepository: MapSnapshotRepository,
    private val settingsRepository: SettingsRepository,
    private val photoProcessor: PhotoProcessor,
    private val photoRepository: PhotoRepository,
    private val clock: Clock
) : ViewModel() {

    val surfaceRequest: StateFlow<SurfaceRequest?> = cameraRepository.surfaceRequest

    private val _events = Channel<CameraEvent>(Channel.BUFFERED)
    val events: Flow<CameraEvent> = _events.receiveAsFlow()

    private data class Permissions(val camera: Boolean = false, val location: Boolean = false, val precise: Boolean = false)

    private data class Controls(
        val lensOverride: LensFacing? = null,
        val flashMode: FlashMode = FlashMode.OFF,
        val captureState: CaptureState = CaptureState.Idle,
        val availableLenses: Set<LensFacing> = emptySet(),
        val cameraBindAttempt: Int = 0
    )

    private val permissions = MutableStateFlow(Permissions())
    private val controls = MutableStateFlow(Controls())

    private val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // ---- Location ------------------------------------------------------------------------

    private val locationRetry = MutableStateFlow(0)
    private val manualFixes = MutableSharedFlow<GpsLocation>(extraBufferCapacity = 1)

    private val locationEnabled: StateFlow<Boolean> = locationRepository.locationEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    /** Live fixes while the screen is visible and location is permitted and switched on. */
    private val location: StateFlow<GpsLocation?> = combine(
        permissions.map { it.location }.distinctUntilChanged(),
        locationEnabled,
        locationRetry
    ) { granted, enabled, _ -> granted && enabled }
        .flatMapLatest<Boolean, GpsLocation?> { active ->
            if (!active) {
                flowOf(null)
            } else {
                merge(locationRepository.locationUpdates(), manualFixes)
                    .catch { Log.w(TAG, "Location updates failed") }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val address: StateFlow<String?> = settings
        .map { it.stampLocationOnPhoto && it.overlay.showAddress }
        .distinctUntilChanged()
        .flatMapLatest { enabled -> if (enabled) addressUpdates() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val heading: StateFlow<Float?> = settings
        .map { it.stampLocationOnPhoto && it.overlay.showDirection }
        .distinctUntilChanged()
        .flatMapLatest { enabled -> if (enabled) headingUpdates() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val mapSnapshot: StateFlow<Bitmap?> = settings
        .map { it.stampLocationOnPhoto && it.overlay.showMap }
        .distinctUntilChanged()
        .flatMapLatest { enabled -> if (enabled) mapUpdates() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Drives the clock on the overlay and re-evaluates staleness when fixes stop arriving. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(clock.nowMs())
            delay(TICK_MS)
        }
    }

    private data class LocationSnapshot(
        val location: GpsLocation?,
        val enabled: Boolean,
        val address: String?,
        val heading: Float?,
        val nowMs: Long
    )

    private data class CameraSnapshot(
        val controls: Controls,
        val zoom: ZoomInfo,
        val hasFlashUnit: Boolean,
        val lastPhoto: Photo?
    )

    private val locationSnapshot = combine(location, locationEnabled, address, heading, ticker) { l, e, a, h, t ->
        LocationSnapshot(l, e, a, h, t)
    }

    private val cameraSnapshot = combine(
        controls,
        cameraRepository.zoom,
        cameraRepository.hasFlashUnit,
        photoRepository.observePhotos().map { it.firstOrNull() }
    ) { c, z, f, p -> CameraSnapshot(c, z, f, p) }

    val uiState: StateFlow<CameraUiState> =
        combine(permissions, settings, locationSnapshot, cameraSnapshot, mapSnapshot, ::buildState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CameraUiState())

    private fun buildState(
        p: Permissions,
        s: AppSettings,
        l: LocationSnapshot,
        c: CameraSnapshot,
        map: Bitmap?
    ): CameraUiState {
        val quality = evaluate(l.location, l.nowMs, s)
        val status = LocationEvaluator.status(p.location, l.enabled, l.location, quality)
        val showable = quality == LocationQuality.USABLE || quality == LocationQuality.LOW_ACCURACY
        val metadata = PhotoMetadataFactory.create(
            location = if (showable) l.location else null,
            address = l.address,
            heading = l.heading,
            capturedAt = Instant.ofEpochMilli(l.nowMs)
        )
        val lens = effectiveLens(c.controls, s)
        return CameraUiState(
            hasCameraPermission = p.camera,
            hasLocationPermission = p.location,
            isPreciseLocation = p.precise,
            captureState = c.controls.captureState,
            location = l.location,
            locationStatus = status,
            address = l.address,
            headingDeg = l.heading,
            flashMode = c.controls.flashMode,
            hasFlashUnit = c.hasFlashUnit,
            lensFacing = lens,
            canSwitchLens = c.controls.availableLenses.size > 1,
            zoom = c.zoom,
            overlayLines = OverlayContentBuilder.build(metadata, s),
            overlayConfig = s.overlay,
            // Hidden with the coordinates when the fix goes stale, as it would be on the photo.
            overlayMap = if (showable) map else null,
            distanceUnit = s.distanceUnit,
            lastPhoto = c.lastPhoto,
            cameraBindAttempt = c.controls.cameraBindAttempt
        )
    }

    // ---- Actions ---------------------------------------------------------------------------

    /** Called on start/resume and after the permission dialog, since grants can change anytime. */
    fun onPermissionsChanged(cameraGranted: Boolean) {
        permissions.value = Permissions(
            camera = cameraGranted,
            location = locationRepository.hasLocationPermission(),
            precise = locationRepository.hasPreciseLocationPermission()
        )
        if (cameraGranted && controls.value.availableLenses.isEmpty()) loadLenses()
    }

    /** Binds the camera to the screen's lifecycle; suspends until the caller's effect is cancelled. */
    suspend fun bindCamera(lifecycleOwner: LifecycleOwner, lens: LensFacing) {
        try {
            cameraRepository.bind(lifecycleOwner, lens, controls.value.flashMode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed", e)
            _events.send(CameraEvent.CameraFailed)
        }
    }

    fun onRetryCamera() = controls.update { it.copy(cameraBindAttempt = it.cameraBindAttempt + 1) }

    fun onSwitchCamera() {
        val current = uiState.value.lensFacing
        val next = if (current == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
        if (next in controls.value.availableLenses) controls.update { it.copy(lensOverride = next) }
    }

    fun onToggleFlash() {
        val next = when (controls.value.flashMode) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        controls.update { it.copy(flashMode = next) }
        cameraRepository.setFlashMode(next)
    }

    fun onFocus(surfaceX: Float, surfaceY: Float) = cameraRepository.focusAt(surfaceX, surfaceY)

    fun onZoomBy(scale: Float) = cameraRepository.setZoomRatio(cameraRepository.zoom.value.ratio * scale)

    fun onDeviceRotation(rotation: Int) = cameraRepository.setTargetRotation(rotation)

    fun onRetryLocation() {
        when {
            !permissions.value.location -> _events.trySend(CameraEvent.RequestLocationPermission)
            !locationEnabled.value -> _events.trySend(CameraEvent.OpenLocationSettings)
            else -> {
                locationRetry.update { it + 1 }
                viewModelScope.launch {
                    locationRepository.getCurrentLocation()?.let { manualFixes.emit(it) }
                }
            }
        }
    }

    fun onCaptureClicked() {
        if (controls.value.captureState != CaptureState.Idle) return
        val s = settings.value
        val p = permissions.value
        val capturedAtMs = clock.nowMs()

        if (p.location && locationEnabled.value && s.lowAccuracyBehavior == LowAccuracyBehavior.WAIT_FOR_ACCURACY) {
            val quality = evaluate(location.value, capturedAtMs, s)
            if (quality != LocationQuality.USABLE) {
                val target = UnitConverter.formatDistance(s.accuracyThresholdM.toDouble(), s.distanceUnit)
                _events.trySend(CameraEvent.Message("Waiting for GPS accuracy of ±$target or better"))
                return
            }
        }

        // Set synchronously so a second tap in the same frame is rejected (TDD §24).
        setCaptureState(CaptureState.Capturing)
        viewModelScope.launch { capture(s, p.location, capturedAtMs) }
    }

    // ---- Capture pipeline ------------------------------------------------------------------

    private data class StampDecision(val location: GpsLocation?, val note: String?)

    private suspend fun capture(s: AppSettings, hasLocationPermission: Boolean, capturedAtMs: Long) {
        var raw: File? = null
        try {
            coroutineScope {
                // Resolve the location in parallel so the shutter fires immediately.
                val stamp = async { resolveStamp(s, hasLocationPermission) }
                val captured = cameraRepository.takePicture()
                raw = captured
                val decision = stamp.await()
                val metadata = PhotoMetadataFactory.create(
                    location = decision.location,
                    address = address.value,
                    heading = heading.value,
                    capturedAt = Instant.ofEpochMilli(capturedAtMs)
                )
                setCaptureState(CaptureState.Processing)
                val processed = photoProcessor.process(captured, metadata, s)
                setCaptureState(CaptureState.Saving)
                val photo = photoRepository.save(processed, metadata)
                setCaptureState(CaptureState.Success(photo))
                _events.send(CameraEvent.PhotoSaved(photo, decision.note))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            val message = if (raw == null) "The camera couldn't take the photo." else "The photo couldn't be saved."
            setCaptureState(CaptureState.Error(message))
            _events.send(CameraEvent.CaptureFailed(message))
        } finally {
            raw?.delete()
            setCaptureState(CaptureState.Idle)
        }
    }

    /** Stale-location protection (TDD §26): never blindly stamp the last fix received. */
    private suspend fun resolveStamp(s: AppSettings, hasLocationPermission: Boolean): StampDecision {
        // Neither shown nor embedded: don't keep location history at all (TDD §29).
        if (!s.stampLocationOnPhoto && !s.embedGpsMetadata) return StampDecision(null, null)
        if (!hasLocationPermission) return StampDecision(null, "Saved without GPS data: location permission is off")
        if (!locationEnabled.value) return StampDecision(null, "Saved without GPS data: location is turned off")

        var loc = location.value
        var quality = evaluate(loc, clock.nowMs(), s)
        if (quality == LocationQuality.STALE || quality == LocationQuality.UNAVAILABLE) {
            val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { locationRepository.getCurrentLocation() }
            if (fresh != null) {
                loc = fresh
                quality = evaluate(fresh, clock.nowMs(), s)
            }
        }
        return when (quality) {
            LocationQuality.UNAVAILABLE, LocationQuality.STALE ->
                StampDecision(null, "Saved without GPS data: no GPS fix yet")
            LocationQuality.LOW_ACCURACY ->
                if (s.lowAccuracyBehavior == LowAccuracyBehavior.CAPTURE_WITHOUT_STAMP) {
                    StampDecision(null, "Saved without GPS data: accuracy was too low")
                } else {
                    val accuracy = loc?.accuracy?.let {
                        " (±" + UnitConverter.formatDistance(it.toDouble(), s.distanceUnit) + ")"
                    }.orEmpty()
                    StampDecision(loc, "Saved with low GPS accuracy$accuracy")
                }
            LocationQuality.USABLE -> StampDecision(loc, null)
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /** Throttled reverse geocoding; see [locationThrottled]. */
    private fun addressUpdates(): Flow<String?> =
        locationThrottled(GEOCODE_MIN_DISTANCE_M, GEOCODE_MIN_INTERVAL_MS, GEOCODE_RETRY_MS) { loc ->
            geocodingRepository.getAddress(loc.latitude, loc.longitude)
        }

    /** Map thumbnail for the viewfinder; re-rendered as the user moves so the pin stays put. */
    private fun mapUpdates(): Flow<Bitmap?> =
        locationThrottled(MAP_MIN_DISTANCE_M, MAP_MIN_INTERVAL_MS, MAP_RETRY_MS) { loc ->
            try {
                mapSnapshotRepository.snapshot(loc.latitude, loc.longitude)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Map thumbnail failed", e)
                null
            }
        }

    /**
     * Runs [fetch] as a loop over the *latest* fix rather than reacting to each fix: fixes arrive
     * every second, so per-fix cancellation would starve a slow lookup. A lookup that is due too
     * soon is postponed (never dropped), and failed (null) lookups are retried after [retryMs], so
     * the result always converges on the current position.
     */
    private fun <T : Any> locationThrottled(
        minDistanceM: Double,
        minIntervalMs: Long,
        retryMs: Long,
        fetch: suspend (GpsLocation) -> T?
    ): Flow<T?> = channelFlow {
        send(null)
        val latest = location.filterNotNull().stateIn(this)
        var attemptedAt: GpsLocation? = null
        var attemptedAtMs = 0L
        var current: T? = null
        while (true) {
            val loc = latest.value
            val last = attemptedAt
            val needsLookup = last == null || current == null ||
                GeoMath.distanceMeters(last.latitude, last.longitude, loc.latitude, loc.longitude) > minDistanceM
            if (!needsLookup) {
                latest.first { it != loc }
                continue
            }
            if (last != null) {
                val interval = if (current == null) retryMs else minIntervalMs
                val wait = attemptedAtMs + interval - clock.nowMs()
                if (wait > 0) {
                    delay(wait)
                    continue // Re-evaluate against whatever the latest fix is now.
                }
            }
            attemptedAt = loc
            attemptedAtMs = clock.nowMs()
            current = fetch(loc)
            send(current)
        }
    }

    private fun headingUpdates(): Flow<Float?> = combine(
        headingRepository.headingUpdates().map<Float, Float?> { it }.onStart { emit(null) },
        location
    ) { magnetic, loc ->
        val trueHeading = if (magnetic != null && loc != null) {
            magnetic + headingRepository.declination(loc.latitude, loc.longitude, loc.altitude ?: 0.0, loc.timestamp)
        } else {
            magnetic
        }
        // Whole degrees: finer precision is noise and only causes recompositions.
        HeadingResolver.resolve(trueHeading, loc)?.let { (it.roundToInt() % 360).toFloat() }
    }.distinctUntilChanged()

    private fun loadLenses() {
        viewModelScope.launch {
            try {
                val lenses = cameraRepository.availableLenses()
                controls.update { it.copy(availableLenses = lenses) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not query cameras", e)
            }
        }
    }

    private fun effectiveLens(c: Controls, s: AppSettings): LensFacing {
        val wanted = c.lensOverride ?: s.defaultLens
        return if (c.availableLenses.isNotEmpty() && wanted !in c.availableLenses) c.availableLenses.first() else wanted
    }

    private fun evaluate(location: GpsLocation?, nowMs: Long, s: AppSettings) = LocationEvaluator.evaluate(
        location = location,
        nowMs = nowMs,
        accuracyThresholdM = s.accuracyThresholdM.toFloat(),
        maxAgeMs = s.maxLocationAgeSec * 1_000L
    )

    private fun setCaptureState(state: CaptureState) = controls.update { it.copy(captureState = state) }

    private companion object {
        const val TAG = "CameraViewModel"
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 1_000L
        const val FRESH_FIX_TIMEOUT_MS = 3_000L
        const val GEOCODE_RETRY_MS = 30_000L
        const val GEOCODE_MIN_INTERVAL_MS = 10_000L
        const val GEOCODE_MIN_DISTANCE_M = 50.0
        const val MAP_RETRY_MS = 15_000L
        const val MAP_MIN_INTERVAL_MS = 3_000L
        const val MAP_MIN_DISTANCE_M = 10.0
    }
}
