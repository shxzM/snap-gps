package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.PhotoMetadata
import java.time.ZoneId
import java.util.Locale

enum class OverlayLineKind { COORDINATES, NO_LOCATION, ADDRESS, DATE_TIME, ALTITUDE, ACCURACY, SPEED, DIRECTION }

data class OverlayLine(val kind: OverlayLineKind, val text: String)

/**
 * Single source of overlay text. Both the live Compose preview and the bitmap burned into the
 * saved JPEG render these lines, so what the user sees is what gets stamped (TDD §14).
 */
object OverlayContentBuilder {

    const val NO_LOCATION_TEXT = "GPS location unavailable"

    fun build(
        metadata: PhotoMetadata,
        settings: AppSettings,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<OverlayLine> {
        val config = settings.overlay
        val lines = mutableListOf<OverlayLine>()
        val stampLocation = settings.stampLocationOnPhoto
        val lat = metadata.latitude
        val lon = metadata.longitude

        if (stampLocation && config.showCoordinates) {
            lines += if (lat != null && lon != null) {
                OverlayLine(
                    OverlayLineKind.COORDINATES,
                    "GPS " + CoordinateFormatter.format(lat, lon, settings.coordinateFormat)
                )
            } else {
                OverlayLine(OverlayLineKind.NO_LOCATION, NO_LOCATION_TEXT)
            }
        }
        if (stampLocation && config.showAddress) {
            metadata.address?.takeIf { it.isNotBlank() }?.let {
                lines += OverlayLine(OverlayLineKind.ADDRESS, it)
            }
        }
        CaptureTimeFormatter.formatDateTime(
            instant = metadata.dateTime,
            pattern = settings.datePattern,
            use24Hour = settings.use24HourTime,
            showDate = config.showDate,
            showTime = config.showTime,
            zone = zone,
            locale = locale
        )?.let { lines += OverlayLine(OverlayLineKind.DATE_TIME, it) }

        if (!stampLocation) return lines

        if (config.showAltitude) {
            metadata.altitude?.let {
                lines += OverlayLine(
                    OverlayLineKind.ALTITUDE,
                    "Altitude: " + UnitConverter.formatDistance(it, settings.distanceUnit)
                )
            }
        }
        if (config.showAccuracy) {
            metadata.accuracy?.let {
                lines += OverlayLine(
                    OverlayLineKind.ACCURACY,
                    "Accuracy: ±" + UnitConverter.formatDistance(it.toDouble(), settings.distanceUnit)
                )
            }
        }
        if (config.showSpeed) {
            metadata.speed?.let {
                lines += OverlayLine(
                    OverlayLineKind.SPEED,
                    "Speed: " + UnitConverter.formatSpeed(it.toDouble(), settings.speedUnit)
                )
            }
        }
        if (config.showDirection) {
            metadata.bearing?.let {
                lines += OverlayLine(OverlayLineKind.DIRECTION, "Direction: " + CardinalDirection.format(it))
            }
        }
        return lines
    }
}
