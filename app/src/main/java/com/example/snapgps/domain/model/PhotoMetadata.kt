package com.example.snapgps.domain.model

import java.time.Instant

/** Everything that gets stamped on, embedded in, and recorded for a captured photo. */
data class PhotoMetadata(
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val address: String?,
    val dateTime: Instant
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}
