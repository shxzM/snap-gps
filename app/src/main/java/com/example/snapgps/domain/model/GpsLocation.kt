package com.example.snapgps.domain.model

/**
 * Platform-independent location fix. [timestamp] is wall-clock UTC millis of the fix,
 * used for stale-location protection.
 */
data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val timestamp: Long
)
