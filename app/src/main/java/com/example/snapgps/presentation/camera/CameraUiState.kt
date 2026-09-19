package com.example.snapgps.presentation.camera

import android.graphics.Bitmap
import com.example.snapgps.domain.format.OverlayLine
import com.example.snapgps.domain.model.CaptureState
import com.example.snapgps.domain.model.DistanceUnit
import com.example.snapgps.domain.model.FlashMode
import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.LensFacing
import com.example.snapgps.domain.model.LocationStatus
import com.example.snapgps.domain.model.OverlayConfig
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.model.ZoomInfo

data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isPreciseLocation: Boolean = false,
    val captureState: CaptureState = CaptureState.Idle,
    val location: GpsLocation? = null,
    val locationStatus: LocationStatus = LocationStatus.Unknown,
    val address: String? = null,
    val headingDeg: Float? = null,
    val flashMode: FlashMode = FlashMode.OFF,
    val hasFlashUnit: Boolean = false,
    val lensFacing: LensFacing = LensFacing.BACK,
    val canSwitchLens: Boolean = false,
    val zoom: ZoomInfo = ZoomInfo(),
    val overlayLines: List<OverlayLine> = emptyList(),
    val overlayConfig: OverlayConfig = OverlayConfig(),
    /** Map thumbnail with a pin at the current fix, or null while unavailable or disabled. */
    val overlayMap: Bitmap? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val lastPhoto: Photo? = null,
    /** Bumped to force a re-bind after a camera failure. */
    val cameraBindAttempt: Int = 0
) {
    val isCapturing: Boolean get() = captureState != CaptureState.Idle
}

sealed interface CameraEvent {
    data class PhotoSaved(val photo: Photo, val note: String?) : CameraEvent
    data class CaptureFailed(val message: String) : CameraEvent
    data object CameraFailed : CameraEvent
    data class Message(val text: String) : CameraEvent
    data object RequestLocationPermission : CameraEvent
    data object OpenLocationSettings : CameraEvent
}
