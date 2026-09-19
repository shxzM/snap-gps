package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.GpsLocation

object HeadingResolver {

    /** Below this speed GPS bearing is noise, so the compass sensor is preferred. */
    const val MIN_SPEED_FOR_GPS_BEARING_MPS = 1f

    fun resolve(sensorHeading: Float?, location: GpsLocation?): Float? {
        val bearing = location?.bearing
        val speed = location?.speed ?: 0f
        return when {
            bearing != null && speed >= MIN_SPEED_FOR_GPS_BEARING_MPS -> bearing
            sensorHeading != null -> sensorHeading
            else -> bearing
        }?.let(CardinalDirection::normalize)
    }
}
