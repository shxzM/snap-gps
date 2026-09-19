package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.CoordinateFormat
import com.example.snapgps.domain.model.DistanceUnit
import com.example.snapgps.domain.model.OverlayConfig
import com.example.snapgps.domain.model.PhotoMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class OverlayContentBuilderTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val full = PhotoMetadata(
        latitude = 25.3176,
        longitude = 82.9739,
        altitude = 81.0,
        accuracy = 8f,
        speed = 2.5f,
        bearing = 128f,
        address = "Varanasi, Uttar Pradesh",
        dateTime = Instant.parse("2026-09-19T12:12:00Z")
    )

    private fun build(metadata: PhotoMetadata = full, settings: AppSettings = AppSettings()) =
        OverlayContentBuilder.build(metadata, settings, zone, Locale.US)

    @Test
    fun `default settings match the TDD example overlay`() {
        assertEquals(
            listOf(
                "GPS 25.3176° N, 82.9739° E",
                "Varanasi, Uttar Pradesh",
                "19 Sep 2026 - 05:42 PM",
                "Altitude: 81 m",
                "Accuracy: ±8 m"
            ),
            build().map { it.text }
        )
    }

    @Test
    fun `optional speed and direction lines`() {
        val settings = AppSettings(overlay = OverlayConfig(showSpeed = true, showDirection = true))
        val lines = build(settings = settings).associate { it.kind to it.text }
        assertEquals("Speed: 9.0 km/h", lines[OverlayLineKind.SPEED])
        assertEquals("Direction: 128° SE", lines[OverlayLineKind.DIRECTION])
    }

    @Test
    fun `each flag hides its line`() {
        val settings = AppSettings(
            overlay = OverlayConfig(
                showCoordinates = false, showAddress = false, showDate = false,
                showTime = false, showAltitude = false, showAccuracy = false
            )
        )
        assertEquals(emptyList<OverlayLine>(), build(settings = settings))
    }

    @Test
    fun `missing location is stated explicitly and dependent lines are skipped`() {
        val noFix = full.copy(latitude = null, longitude = null, altitude = null, accuracy = null, address = null)
        assertEquals(
            listOf(OverlayLineKind.NO_LOCATION, OverlayLineKind.DATE_TIME),
            build(noFix).map { it.kind }
        )
    }

    @Test
    fun `location stamping disabled leaves only date and time`() {
        assertEquals(
            listOf(OverlayLineKind.DATE_TIME),
            build(settings = AppSettings(stampLocationOnPhoto = false)).map { it.kind }
        )
    }

    @Test
    fun `respects coordinate format and units`() {
        val settings = AppSettings(coordinateFormat = CoordinateFormat.DMS, distanceUnit = DistanceUnit.IMPERIAL)
        val lines = build(settings = settings).associate { it.kind to it.text }
        assertEquals("GPS 25°19'03.4\" N, 82°58'26.0\" E", lines[OverlayLineKind.COORDINATES])
        assertEquals("Altitude: 266 ft", lines[OverlayLineKind.ALTITUDE])
        assertEquals("Accuracy: ±26 ft", lines[OverlayLineKind.ACCURACY])
    }
}
