package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.DistanceUnit
import com.example.snapgps.domain.model.SpeedUnit
import java.util.Locale
import kotlin.math.roundToInt

object UnitConverter {

    const val FEET_PER_METER = 3.28084
    const val KMH_PER_MPS = 3.6
    const val MPH_PER_MPS = 2.236936

    fun metersTo(meters: Double, unit: DistanceUnit): Double = when (unit) {
        DistanceUnit.METRIC -> meters
        DistanceUnit.IMPERIAL -> meters * FEET_PER_METER
    }

    /** Whole units: `81 m` / `266 ft`. */
    fun formatDistance(meters: Double, unit: DistanceUnit): String {
        val value = metersTo(meters, unit).roundToInt()
        return "$value ${distanceSymbol(unit)}"
    }

    fun distanceSymbol(unit: DistanceUnit): String = when (unit) {
        DistanceUnit.METRIC -> "m"
        DistanceUnit.IMPERIAL -> "ft"
    }

    fun speedFromMps(metersPerSecond: Double, unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.KMH -> metersPerSecond * KMH_PER_MPS
        SpeedUnit.MPH -> metersPerSecond * MPH_PER_MPS
        SpeedUnit.MPS -> metersPerSecond
    }

    /** One decimal: `12.3 km/h`. */
    fun formatSpeed(metersPerSecond: Double, unit: SpeedUnit): String =
        String.format(Locale.US, "%.1f %s", speedFromMps(metersPerSecond, unit), speedSymbol(unit))

    fun speedSymbol(unit: SpeedUnit): String = when (unit) {
        SpeedUnit.KMH -> "km/h"
        SpeedUnit.MPH -> "mph"
        SpeedUnit.MPS -> "m/s"
    }
}
