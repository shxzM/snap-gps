package com.example.snapgps.domain.model

/** Location state machine (TDD §25). */
sealed interface LocationStatus {
    data object Unknown : LocationStatus
    data object PermissionRequired : LocationStatus
    data object Disabled : LocationStatus
    data object Searching : LocationStatus
    data class LowAccuracy(val accuracyM: Float?) : LocationStatus
    data class Ready(val accuracyM: Float?) : LocationStatus
}

/** Capture state machine (TDD §24). Anything but [Idle] blocks a new capture. */
sealed interface CaptureState {
    data object Idle : CaptureState
    data object Capturing : CaptureState
    data object Processing : CaptureState
    data object Saving : CaptureState
    data class Success(val photo: Photo) : CaptureState
    data class Error(val message: String) : CaptureState
}

data class ZoomInfo(
    val ratio: Float = 1f,
    val minRatio: Float = 1f,
    val maxRatio: Float = 1f
)
