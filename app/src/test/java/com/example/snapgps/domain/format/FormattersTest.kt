package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.CoordinateFormat
import com.example.snapgps.domain.model.DatePattern
import com.example.snapgps.domain.model.DistanceUnit
import com.example.snapgps.domain.model.SpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class CoordinateFormatterTest {

    @Test
    fun `decimal format uses four places and hemispheres`() {
        assertEquals("25.3176° N, 82.9739° E", CoordinateFormatter.formatDecimal(25.3176, 82.9739))
        assertEquals("33.8688° S, 151.2093° W", CoordinateFormatter.formatDecimal(-33.8688, -151.2093))
    }

    @Test
    fun `DMS format converts minutes and seconds`() {
        assertEquals("25°19'03.4\" N, 82°58'26.0\" E", CoordinateFormatter.formatDms(25.3176, 82.9739))
    }

    @Test
    fun `DMS rounding never produces 60 seconds`() {
        assertEquals("11°00'00.0\" N, 0°00'00.0\" E", CoordinateFormatter.formatDms(10.99999999, 0.0))
    }

    @Test
    fun `zero is north and east`() {
        assertEquals("0.0000° N, 0.0000° E", CoordinateFormatter.format(0.0, 0.0, CoordinateFormat.DECIMAL))
    }
}

class UnitConverterTest {

    @Test
    fun `distance in meters and feet`() {
        assertEquals("81 m", UnitConverter.formatDistance(81.0, DistanceUnit.METRIC))
        assertEquals("266 ft", UnitConverter.formatDistance(81.0, DistanceUnit.IMPERIAL))
    }

    @Test
    fun `speed conversions`() {
        assertEquals("36.0 km/h", UnitConverter.formatSpeed(10.0, SpeedUnit.KMH))
        assertEquals("22.4 mph", UnitConverter.formatSpeed(10.0, SpeedUnit.MPH))
        assertEquals("10.0 m/s", UnitConverter.formatSpeed(10.0, SpeedUnit.MPS))
    }
}

class CaptureTimeFormatterTest {

    private val instant = Instant.parse("2026-09-19T12:12:00Z")
    private val kolkata = ZoneId.of("Asia/Kolkata")

    @Test
    fun `formats date and 12 hour time in the given zone`() {
        assertEquals(
            "19 Sep 2026 - 05:42 PM",
            CaptureTimeFormatter.formatDateTime(instant, DatePattern.DAY_MONTH_YEAR, false, zone = kolkata, locale = Locale.US)
        )
    }

    @Test
    fun `24 hour time and ISO date`() {
        assertEquals("17:42", CaptureTimeFormatter.formatTime(instant, true, kolkata, Locale.US))
        assertEquals("2026-09-19", CaptureTimeFormatter.formatDate(instant, DatePattern.ISO, kolkata, Locale.US))
    }

    @Test
    fun `hidden halves are omitted`() {
        assertEquals(
            "05:42 PM",
            CaptureTimeFormatter.formatDateTime(instant, DatePattern.ISO, false, showDate = false, zone = kolkata, locale = Locale.US)
        )
        assertNull(
            CaptureTimeFormatter.formatDateTime(instant, DatePattern.ISO, false, showDate = false, showTime = false)
        )
    }
}

class CardinalDirectionTest {

    @Test
    fun `maps angles to the nearest of eight directions`() {
        assertEquals("N", CardinalDirection.of(0f))
        assertEquals("N", CardinalDirection.of(22.4f))
        assertEquals("NE", CardinalDirection.of(22.5f))
        assertEquals("SE", CardinalDirection.of(128f))
        assertEquals("N", CardinalDirection.of(359f))
    }

    @Test
    fun `wraps negative and large angles`() {
        assertEquals("NW", CardinalDirection.of(-45f))
        assertEquals("N", CardinalDirection.of(720f))
        assertEquals(350f, CardinalDirection.normalize(-10f), 0.001f)
    }

    @Test
    fun `format rounds and wraps 360 to 0`() {
        assertEquals("128° SE", CardinalDirection.format(128f))
        assertEquals("0° N", CardinalDirection.format(359.7f))
    }
}

class GeoMathTest {

    @Test
    fun `one degree of longitude at the equator is about 111 km`() {
        assertEquals(111_195.0, GeoMath.distanceMeters(0.0, 0.0, 0.0, 1.0), 1.0)
        assertEquals(0.0, GeoMath.distanceMeters(25.3, 82.9, 25.3, 82.9), 0.0001)
    }
}
