package com.example.snapgps.data.media

import androidx.exifinterface.media.ExifInterface
import com.example.snapgps.domain.model.PhotoMetadata
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Writes machine-readable capture data (TDD §16). GPS tags are strictly opt-in via [write]'s flag. */
class ExifWriter {

    fun write(target: File, source: File, metadata: PhotoMetadata, embedGps: Boolean) {
        val exif = ExifInterface(target)
        copyCameraTags(source, exif)

        val local = metadata.dateTime.atZone(ZoneId.systemDefault())
        val dateTime = EXIF_DATE_TIME.format(local)
        val offset = OFFSET.format(local)
        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        // Pixels were rotated upright during processing.
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "SnapGPS")

        val lat = metadata.latitude
        val lon = metadata.longitude
        if (embedGps && lat != null && lon != null) {
            exif.setLatLong(lat, lon)
            metadata.altitude?.let(exif::setAltitude)
            metadata.speed?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.GPS_SPEED_KILOMETERS_PER_HOUR)
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED, rational(it * 3.6))
            }
            metadata.bearing?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.GPS_DIRECTION_TRUE)
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, rational(it.toDouble()))
            }
            metadata.accuracy?.let {
                exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, rational(it.toDouble()))
            }
            val utc = metadata.dateTime.atZone(ZoneOffset.UTC)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, GPS_DATE.format(utc))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, GPS_TIME.format(utc))
        } else {
            GPS_TAGS.forEach { exif.setAttribute(it, null) }
        }
        exif.saveAttributes()
    }

    private fun copyCameraTags(source: File, target: ExifInterface) {
        val original = runCatching { ExifInterface(source) }.getOrNull() ?: return
        CAMERA_TAGS.forEach { tag -> original.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
    }

    private fun rational(value: Double): String = "${(value * 100).roundToLong()}/100"

    private companion object {
        val EXIF_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
        val OFFSET: DateTimeFormatter = DateTimeFormatter.ofPattern("xxx", Locale.US)
        val GPS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.US)
        val GPS_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

        val CAMERA_TAGS = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE
        )

        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_TIMESTAMP
        )
    }
}
