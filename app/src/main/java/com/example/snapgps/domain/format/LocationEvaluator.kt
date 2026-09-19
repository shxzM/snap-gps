package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.LocationStatus

enum class LocationQuality { UNAVAILABLE, STALE, LOW_ACCURACY, USABLE }

/** Stale-location protection (TDD §26). The same rules drive the status chip and the capture check. */
object LocationEvaluator {

    fun evaluate(
        location: GpsLocation?,
        nowMs: Long,
        accuracyThresholdM: Float,
        maxAgeMs: Long
    ): LocationQuality {
        if (location == null) return LocationQuality.UNAVAILABLE
        // A fix time slightly in the future (clock skew) counts as fresh.
        if (nowMs - location.timestamp > maxAgeMs) return LocationQuality.STALE
        val accuracy = location.accuracy ?: return LocationQuality.LOW_ACCURACY
        return if (accuracy <= accuracyThresholdM) LocationQuality.USABLE else LocationQuality.LOW_ACCURACY
    }

    fun status(
        hasPermission: Boolean,
        locationEnabled: Boolean,
        location: GpsLocation?,
        quality: LocationQuality
    ): LocationStatus = when {
        !hasPermission -> LocationStatus.PermissionRequired
        !locationEnabled -> LocationStatus.Disabled
        quality == LocationQuality.UNAVAILABLE || quality == LocationQuality.STALE -> LocationStatus.Searching
        quality == LocationQuality.LOW_ACCURACY -> LocationStatus.LowAccuracy(location?.accuracy)
        else -> LocationStatus.Ready(location?.accuracy)
    }
}
