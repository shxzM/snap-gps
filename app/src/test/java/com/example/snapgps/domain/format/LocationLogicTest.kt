package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.LocationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

private fun fix(
    accuracy: Float? = 5f,
    timestamp: Long = 1_000_000L,
    speed: Float? = null,
    bearing: Float? = null
) = GpsLocation(25.3176, 82.9739, 81.0, accuracy, speed, bearing, timestamp)

class LocationEvaluatorTest {

    private val now = 1_000_000L
    private fun evaluate(location: GpsLocation?, nowMs: Long = now) =
        LocationEvaluator.evaluate(location, nowMs, accuracyThresholdM = 20f, maxAgeMs = 30_000L)

    @Test
    fun `no location is unavailable`() = assertEquals(LocationQuality.UNAVAILABLE, evaluate(null))

    @Test
    fun `old location is stale even if accurate`() =
        assertEquals(LocationQuality.STALE, evaluate(fix(accuracy = 3f, timestamp = now - 30_001)))

    @Test
    fun `location exactly at max age is still fresh`() =
        assertEquals(LocationQuality.USABLE, evaluate(fix(timestamp = now - 30_000)))

    @Test
    fun `future timestamp from clock skew counts as fresh`() =
        assertEquals(LocationQuality.USABLE, evaluate(fix(timestamp = now + 2_000)))

    @Test
    fun `accuracy above threshold is low`() =
        assertEquals(LocationQuality.LOW_ACCURACY, evaluate(fix(accuracy = 85f)))

    @Test
    fun `accuracy equal to threshold is usable`() =
        assertEquals(LocationQuality.USABLE, evaluate(fix(accuracy = 20f)))

    @Test
    fun `unknown accuracy is treated as low`() =
        assertEquals(LocationQuality.LOW_ACCURACY, evaluate(fix(accuracy = null)))

    @Test
    fun `status follows the TDD state machine`() {
        val loc = fix(accuracy = 7f)
        assertEquals(
            LocationStatus.PermissionRequired,
            LocationEvaluator.status(false, true, loc, LocationQuality.USABLE)
        )
        assertEquals(LocationStatus.Disabled, LocationEvaluator.status(true, false, loc, LocationQuality.USABLE))
        assertEquals(LocationStatus.Searching, LocationEvaluator.status(true, true, null, LocationQuality.UNAVAILABLE))
        assertEquals(LocationStatus.Searching, LocationEvaluator.status(true, true, loc, LocationQuality.STALE))
        assertEquals(LocationStatus.LowAccuracy(7f), LocationEvaluator.status(true, true, loc, LocationQuality.LOW_ACCURACY))
        assertEquals(LocationStatus.Ready(7f), LocationEvaluator.status(true, true, loc, LocationQuality.USABLE))
    }
}

class HeadingResolverTest {

    @Test
    fun `GPS bearing wins when moving`() =
        assertEquals(90f, HeadingResolver.resolve(10f, fix(speed = 5f, bearing = 90f))!!, 0.001f)

    @Test
    fun `sensor wins when standing still`() =
        assertEquals(10f, HeadingResolver.resolve(10f, fix(speed = 0.2f, bearing = 90f))!!, 0.001f)

    @Test
    fun `falls back to GPS bearing without a sensor`() =
        assertEquals(90f, HeadingResolver.resolve(null, fix(speed = 0f, bearing = 90f))!!, 0.001f)

    @Test
    fun `nothing available is null`() = assertNull(HeadingResolver.resolve(null, fix()))

    @Test
    fun `result is normalized`() = assertEquals(350f, HeadingResolver.resolve(-10f, null)!!, 0.001f)
}

class PhotoMetadataFactoryTest {

    private val time = Instant.parse("2026-09-19T12:12:00Z")

    @Test
    fun `copies location fields`() {
        val m = PhotoMetadataFactory.create(fix(accuracy = 8f, speed = 1f), "Varanasi", 128f, time)
        assertEquals(25.3176, m.latitude!!, 0.0)
        assertEquals(82.9739, m.longitude!!, 0.0)
        assertEquals(81.0, m.altitude!!, 0.0)
        assertEquals(8f, m.accuracy!!, 0f)
        assertEquals(128f, m.bearing!!, 0f)
        assertEquals("Varanasi", m.address)
        assertEquals(time, m.dateTime)
    }

    @Test
    fun `no location drops every location-derived field`() {
        val m = PhotoMetadataFactory.create(null, "Varanasi", 128f, time)
        assertNull(m.latitude)
        assertNull(m.longitude)
        assertNull(m.address)
        assertNull(m.bearing)
        assertEquals(time, m.dateTime)
    }
}
