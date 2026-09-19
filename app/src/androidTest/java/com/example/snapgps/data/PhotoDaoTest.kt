package com.example.snapgps.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.snapgps.data.local.AppDatabase
import com.example.snapgps.data.local.PhotoDao
import com.example.snapgps.data.local.PhotoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PhotoDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.photoDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(capturedAt: Long, uri: String = "content://media/$capturedAt") = PhotoEntity(
        uri = uri, latitude = 25.3, longitude = 82.9, altitude = null, accuracy = 5f,
        speed = null, bearing = null, address = null, capturedAt = capturedAt
    )

    @Test
    fun insertedPhotosAreObservedNewestFirst() = runTest {
        dao.insert(entity(1_000))
        dao.insert(entity(3_000))
        dao.insert(entity(2_000))

        assertEquals(listOf(3_000L, 2_000L, 1_000L), dao.observeAll().first().map { it.capturedAt })
    }

    @Test
    fun deleteRemovesOnlyThatPhoto() = runTest {
        val keep = dao.insert(entity(1_000))
        val drop = dao.insert(entity(2_000))

        dao.deleteById(drop)

        assertNull(dao.observeById(drop).first())
        assertEquals(listOf(keep), dao.getAll().map { it.id })
    }

    @Test
    fun deleteByIdsRemovesBatch() = runTest {
        val ids = (1..3).map { dao.insert(entity(it * 1_000L)) }
        dao.deleteByIds(ids.take(2))
        assertEquals(listOf(ids[2]), dao.getAll().map { it.id })
    }
}
