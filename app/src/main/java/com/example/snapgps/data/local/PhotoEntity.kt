package com.example.snapgps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.snapgps.domain.model.Photo

/** App-level record of a saved photo; the image itself lives in MediaStore at [uri]. */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val address: String?,
    val capturedAt: Long,
    val overlayConfigId: String? = null
)

fun PhotoEntity.toDomain() = Photo(
    id = id,
    uri = uri,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    accuracy = accuracy,
    speed = speed,
    bearing = bearing,
    address = address,
    capturedAt = capturedAt
)
