package com.example.snapgps.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapgps.domain.format.OverlayContentBuilder
import com.example.snapgps.domain.format.OverlayLine
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.OverlayConfig
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class SettingsState(
    val settings: AppSettings,
    /** Overlay rendered from sample data, so format changes are visible immediately. */
    val previewLines: List<OverlayLine>
)

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val state: StateFlow<SettingsState?> = settingsRepository.settings
        .map { SettingsState(it, OverlayContentBuilder.build(SAMPLE, it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun updateOverlay(transform: (OverlayConfig) -> OverlayConfig) = update { it.copy(overlay = transform(it.overlay)) }

    private companion object {
        val SAMPLE = PhotoMetadata(
            latitude = 25.3176,
            longitude = 82.9739,
            altitude = 81.0,
            accuracy = 8f,
            speed = 1.4f,
            bearing = 128f,
            address = "Varanasi, Uttar Pradesh",
            dateTime = Instant.parse("2026-09-19T12:12:00Z")
        )
    }
}
