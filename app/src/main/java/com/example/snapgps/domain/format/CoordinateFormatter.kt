package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.CoordinateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object CoordinateFormatter {

    fun format(latitude: Double, longitude: Double, format: CoordinateFormat): String = when (format) {
        CoordinateFormat.DECIMAL -> formatDecimal(latitude, longitude)
        CoordinateFormat.DMS -> formatDms(latitude, longitude)
    }

    /** `25.3176° N, 82.9739° E` */
    fun formatDecimal(latitude: Double, longitude: Double): String =
        "${decimal(latitude)}° ${latHemisphere(latitude)}, ${decimal(longitude)}° ${lonHemisphere(longitude)}"

    /** `25°19'03.4" N, 82°58'26.0" E` */
    fun formatDms(latitude: Double, longitude: Double): String =
        "${dms(latitude)} ${latHemisphere(latitude)}, ${dms(longitude)} ${lonHemisphere(longitude)}"

    fun latHemisphere(latitude: Double): String = if (latitude >= 0) "N" else "S"

    fun lonHemisphere(longitude: Double): String = if (longitude >= 0) "E" else "W"

    private fun decimal(value: Double): String = String.format(Locale.US, "%.4f", abs(value))

    private fun dms(value: Double): String {
        // Work in tenths of an arc-second so rounding can never produce 60" or 60'.
        val totalTenths = (abs(value) * 36_000).roundToLong()
        val degrees = totalTenths / 36_000
        val minutes = (totalTenths % 36_000) / 600
        val tenthsOfSecond = totalTenths % 600
        return String.format(
            Locale.US, "%d°%02d'%02d.%d\"",
            degrees, minutes, tenthsOfSecond / 10, tenthsOfSecond % 10
        )
    }
}
