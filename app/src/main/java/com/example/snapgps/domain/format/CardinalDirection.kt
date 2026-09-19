package com.example.snapgps.domain.format

import kotlin.math.roundToInt

object CardinalDirection {

    private val NAMES = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** Maps any angle into [0, 360). */
    fun normalize(degrees: Float): Float {
        val d = degrees % 360f
        return if (d < 0f) d + 360f else d
    }

    fun of(degrees: Float): String = NAMES[((normalize(degrees) + 22.5f) / 45f).toInt() % 8]

    /** `128° SE` */
    fun format(degrees: Float): String {
        val whole = normalize(degrees).roundToInt() % 360
        return "$whole° ${of(degrees)}"
    }
}
