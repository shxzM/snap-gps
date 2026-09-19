package com.example.snapgps.domain.format

import com.example.snapgps.domain.model.DatePattern
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CaptureTimeFormatter {

    fun formatDate(
        instant: Instant,
        pattern: DatePattern,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = DateTimeFormatter.ofPattern(pattern.pattern, locale).withZone(zone).format(instant)

    fun formatTime(
        instant: Instant,
        use24Hour: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val pattern = if (use24Hour) "HH:mm" else "hh:mm a"
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(instant)
    }

    /** `19 Sep 2026 - 05:42 PM`, or just one half when the other is hidden. */
    fun formatDateTime(
        instant: Instant,
        pattern: DatePattern,
        use24Hour: Boolean,
        showDate: Boolean = true,
        showTime: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String? {
        val date = if (showDate) formatDate(instant, pattern, zone, locale) else null
        val time = if (showTime) formatTime(instant, use24Hour, zone, locale) else null
        return listOfNotNull(date, time).joinToString(" - ").ifEmpty { null }
    }
}
