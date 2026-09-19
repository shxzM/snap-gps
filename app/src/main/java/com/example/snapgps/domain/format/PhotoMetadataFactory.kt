package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.PhotoMetadata
import java.time.Instant

object PhotoMetadataFactory {

    /** Pass `location = null` to produce a photo with no location data at all. */
    fun create(
        location: GpsLocation?,
        address: String?,
        heading: Float?,
        capturedAt: Instant
    ): PhotoMetadata = PhotoMetadata(
        latitude = location?.latitude,
        longitude = location?.longitude,
        altitude = location?.altitude,
        accuracy = location?.accuracy,
        speed = location?.speed,
        bearing = if (location != null) heading else null,
        address = if (location != null) address else null,
        dateTime = capturedAt
    )
}
