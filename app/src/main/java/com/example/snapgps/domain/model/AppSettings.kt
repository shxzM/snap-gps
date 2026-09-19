package com.example.snapgps.domain.model

data class AppSettings(
    val overlay: OverlayConfig = OverlayConfig(),
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val datePattern: DatePattern = DatePattern.DAY_MONTH_YEAR,
    val use24HourTime: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultLens: LensFacing = LensFacing.BACK,
    /** Whether location details are drawn on the photo at all. */
    val stampLocationOnPhoto: Boolean = true,
    /** Whether GPS tags are written into EXIF. Independent of the visible overlay. */
    val embedGpsMetadata: Boolean = true,
    val accuracyThresholdM: Int = 20,
    val maxLocationAgeSec: Int = 30,
    val lowAccuracyBehavior: LowAccuracyBehavior = LowAccuracyBehavior.STAMP_WITH_WARNING
)

enum class CoordinateFormat { DECIMAL, DMS }

enum class DatePattern(val pattern: String) {
    DAY_MONTH_YEAR("dd MMM yyyy"),
    MONTH_DAY_YEAR("MMM dd, yyyy"),
    ISO("yyyy-MM-dd")
}

enum class DistanceUnit { METRIC, IMPERIAL }

enum class SpeedUnit { KMH, MPH, MPS }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LensFacing { BACK, FRONT }

enum class FlashMode { OFF, ON, AUTO }

/** What to do when the shutter is pressed while the fix is worse than the threshold. */
enum class LowAccuracyBehavior {
    /** Stamp the best available fix and warn the user. */
    STAMP_WITH_WARNING,
    /** Refuse to capture until accuracy meets the threshold. */
    WAIT_FOR_ACCURACY,
    /** Capture, but without any location data. */
    CAPTURE_WITHOUT_STAMP
}
