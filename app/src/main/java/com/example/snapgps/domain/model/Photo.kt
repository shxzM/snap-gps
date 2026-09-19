package com.example.snapgps.domain.model

data class Photo(
    val id: Long,
    val uri: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val address: String?,
    val capturedAt: Long
)
