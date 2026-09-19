package com.example.snapgps.presentation.gallery

import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailRowsTest {

    private val photo = Photo(
        id = 1, uri = "content://x", latitude = 25.3176, longitude = 82.9739, altitude = 81.0,
        accuracy = 8f, speed = null, bearing = null, address = "Varanasi", capturedAt = 0L
    )

    @Test
    fun `lists available details in order`() {
        assertEquals(
            listOf("Coordinates", "Address", "Captured", "Accuracy", "Altitude"),
            detailRows(photo, AppSettings()).map { it.label }
        )
    }

    @Test
    fun `photo without GPS says so`() {
        val rows = detailRows(photo.copy(latitude = null, longitude = null), AppSettings())
        assertEquals("Not recorded", rows.first { it.label == "Coordinates" }.value)
    }
}
