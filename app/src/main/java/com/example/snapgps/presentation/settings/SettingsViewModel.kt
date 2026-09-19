package com.example.snapgps.presentation.settings

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapgps.domain.format.OverlayContentBuilder
import com.example.snapgps.domain.format.OverlayLine
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.OverlayConfig
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.domain.repository.MapSnapshotRepository
import com.example.snapgps.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.time.Instant

data class SettingsState(
    val settings: AppSettings,
    /** Overlay rendered from sample data, so format changes are visible immediately. */
    val previewLines: List<OverlayLine>,
    /** Map thumbnail of the sample location; null while loading, offline, or disabled. */
    val previewMap: Bitmap? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val mapSnapshotRepository: MapSnapshotRepository
) : ViewModel() {

    private val previewMap: Flow<Bitmap?> = settingsRepository.settings
        .map { it.stampLocationOnPhoto && it.overlay.showMap }
        .distinctUntilChanged()
        .transformLatest { enabled ->
            emit(null)
            if (enabled) emit(sampleMap())
        }

    val state: StateFlow<SettingsState?> = combine(settingsRepository.settings, previewMap) { settings, map ->
        SettingsState(settings, OverlayContentBuilder.build(SAMPLE, settings), map)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun updateOverlay(transform: (OverlayConfig) -> OverlayConfig) = update { it.copy(overlay = transform(it.overlay)) }

    private suspend fun sampleMap(): Bitmap? {
        val lat = SAMPLE.latitude ?: return null
        val lon = SAMPLE.longitude ?: return null
        return try {
            mapSnapshotRepository.snapshot(lat, lon)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

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
