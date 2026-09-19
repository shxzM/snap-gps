package com.example.snapgps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos ORDER BY capturedAt DESC, id DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun observeById(id: Long): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos")
    suspend fun getAll(): List<PhotoEntity>

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
