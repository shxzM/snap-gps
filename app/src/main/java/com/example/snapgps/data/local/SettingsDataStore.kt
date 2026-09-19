package com.example.snapgps.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.OverlayConfig
import com.example.snapgps.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .map(::read)
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private object Keys {
        val showCoordinates = booleanPreferencesKey("overlay_show_coordinates")
        val showAddress = booleanPreferencesKey("overlay_show_address")
        val showDate = booleanPreferencesKey("overlay_show_date")
        val showTime = booleanPreferencesKey("overlay_show_time")
        val showAltitude = booleanPreferencesKey("overlay_show_altitude")
        val showAccuracy = booleanPreferencesKey("overlay_show_accuracy")
        val showSpeed = booleanPreferencesKey("overlay_show_speed")
        val showDirection = booleanPreferencesKey("overlay_show_direction")
        val overlayPosition = stringPreferencesKey("overlay_position")
        val overlayOpacity = floatPreferencesKey("overlay_opacity")
        val coordinateFormat = stringPreferencesKey("coordinate_format")
        val datePattern = stringPreferencesKey("date_pattern")
        val use24HourTime = booleanPreferencesKey("use_24_hour_time")
        val distanceUnit = stringPreferencesKey("distance_unit")
        val speedUnit = stringPreferencesKey("speed_unit")
        val themeMode = stringPreferencesKey("theme_mode")
        val defaultLens = stringPreferencesKey("default_lens")
        val stampLocation = booleanPreferencesKey("stamp_location_on_photo")
        val embedGpsMetadata = booleanPreferencesKey("embed_gps_metadata")
        val accuracyThreshold = intPreferencesKey("accuracy_threshold_m")
        val maxLocationAge = intPreferencesKey("max_location_age_sec")
        val lowAccuracyBehavior = stringPreferencesKey("low_accuracy_behavior")
    }

    private fun read(prefs: Preferences): AppSettings {
        val d = AppSettings()
        val o = d.overlay
        return AppSettings(
            overlay = OverlayConfig(
                showCoordinates = prefs[Keys.showCoordinates] ?: o.showCoordinates,
                showAddress = prefs[Keys.showAddress] ?: o.showAddress,
                showDate = prefs[Keys.showDate] ?: o.showDate,
                showTime = prefs[Keys.showTime] ?: o.showTime,
                showAltitude = prefs[Keys.showAltitude] ?: o.showAltitude,
                showAccuracy = prefs[Keys.showAccuracy] ?: o.showAccuracy,
                showSpeed = prefs[Keys.showSpeed] ?: o.showSpeed,
                showDirection = prefs[Keys.showDirection] ?: o.showDirection,
                position = prefs[Keys.overlayPosition].toEnum(o.position),
                opacity = prefs[Keys.overlayOpacity] ?: o.opacity
            ),
            coordinateFormat = prefs[Keys.coordinateFormat].toEnum(d.coordinateFormat),
            datePattern = prefs[Keys.datePattern].toEnum(d.datePattern),
            use24HourTime = prefs[Keys.use24HourTime] ?: d.use24HourTime,
            distanceUnit = prefs[Keys.distanceUnit].toEnum(d.distanceUnit),
            speedUnit = prefs[Keys.speedUnit].toEnum(d.speedUnit),
            themeMode = prefs[Keys.themeMode].toEnum(d.themeMode),
            defaultLens = prefs[Keys.defaultLens].toEnum(d.defaultLens),
            stampLocationOnPhoto = prefs[Keys.stampLocation] ?: d.stampLocationOnPhoto,
            embedGpsMetadata = prefs[Keys.embedGpsMetadata] ?: d.embedGpsMetadata,
            accuracyThresholdM = prefs[Keys.accuracyThreshold] ?: d.accuracyThresholdM,
            maxLocationAgeSec = prefs[Keys.maxLocationAge] ?: d.maxLocationAgeSec,
            lowAccuracyBehavior = prefs[Keys.lowAccuracyBehavior].toEnum(d.lowAccuracyBehavior)
        )
    }

    private fun write(prefs: MutablePreferences, s: AppSettings) {
        val o = s.overlay
        prefs[Keys.showCoordinates] = o.showCoordinates
        prefs[Keys.showAddress] = o.showAddress
        prefs[Keys.showDate] = o.showDate
        prefs[Keys.showTime] = o.showTime
        prefs[Keys.showAltitude] = o.showAltitude
        prefs[Keys.showAccuracy] = o.showAccuracy
        prefs[Keys.showSpeed] = o.showSpeed
        prefs[Keys.showDirection] = o.showDirection
        prefs[Keys.overlayPosition] = o.position.name
        prefs[Keys.overlayOpacity] = o.opacity
        prefs[Keys.coordinateFormat] = s.coordinateFormat.name
        prefs[Keys.datePattern] = s.datePattern.name
        prefs[Keys.use24HourTime] = s.use24HourTime
        prefs[Keys.distanceUnit] = s.distanceUnit.name
        prefs[Keys.speedUnit] = s.speedUnit.name
        prefs[Keys.themeMode] = s.themeMode.name
        prefs[Keys.defaultLens] = s.defaultLens.name
        prefs[Keys.stampLocation] = s.stampLocationOnPhoto
        prefs[Keys.embedGpsMetadata] = s.embedGpsMetadata
        prefs[Keys.accuracyThreshold] = s.accuracyThresholdM
        prefs[Keys.maxLocationAge] = s.maxLocationAgeSec
        prefs[Keys.lowAccuracyBehavior] = s.lowAccuracyBehavior.name
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
