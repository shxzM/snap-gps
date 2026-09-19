package com.example.snapgps.presentation.gallery

import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.snapgps.domain.format.CaptureTimeFormatter
import com.example.snapgps.domain.format.CardinalDirection
import com.example.snapgps.domain.format.CoordinateFormatter
import com.example.snapgps.domain.format.UnitConverter
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.repository.DeleteResult
import com.example.snapgps.domain.repository.PhotoRepository
import com.example.snapgps.domain.repository.SettingsRepository
import com.example.snapgps.presentation.navigation.PhotoDetailRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class DetailRow(val label: String, val value: String)

data class PhotoDetailState(
    val isLoading: Boolean = true,
    val photo: Photo? = null,
    val rows: List<DetailRow> = emptyList()
)

sealed interface PhotoDetailEvent {
    data object Close : PhotoDetailEvent
    data class RequestDeleteConsent(val intentSender: IntentSender) : PhotoDetailEvent
    data class Message(val text: String) : PhotoDetailEvent
}

class PhotoDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val photoRepository: PhotoRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val photoId = savedStateHandle.toRoute<PhotoDetailRoute>().photoId

    private val _events = Channel<PhotoDetailEvent>(Channel.BUFFERED)
    val events: Flow<PhotoDetailEvent> = _events.receiveAsFlow()

    val state: StateFlow<PhotoDetailState> =
        combine(photoRepository.observePhoto(photoId), settingsRepository.settings) { photo, settings ->
            PhotoDetailState(isLoading = false, photo = photo, rows = photo?.let { detailRows(it, settings) }.orEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoDetailState())

    fun onDeleteConfirmed() {
        val photo = state.value.photo ?: return
        viewModelScope.launch {
            try {
                when (val result = photoRepository.delete(photo)) {
                    DeleteResult.Deleted -> _events.send(PhotoDetailEvent.Close)
                    is DeleteResult.NeedsConsent -> _events.send(PhotoDetailEvent.RequestDeleteConsent(result.intentSender))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                _events.send(PhotoDetailEvent.Message("The photo couldn't be deleted."))
            }
        }
    }

    /** The system deleted the media itself; only our record remains. */
    fun onDeleteConsentResult(granted: Boolean) {
        if (!granted) return
        viewModelScope.launch {
            photoRepository.removeRecord(photoId)
            _events.send(PhotoDetailEvent.Close)
        }
    }

    private companion object {
        const val TAG = "PhotoDetailViewModel"
    }
}

internal fun detailRows(photo: Photo, settings: AppSettings): List<DetailRow> = buildList {
    val lat = photo.latitude
    val lon = photo.longitude
    if (lat != null && lon != null) {
        add(DetailRow("Coordinates", CoordinateFormatter.format(lat, lon, settings.coordinateFormat)))
    } else {
        add(DetailRow("Coordinates", "Not recorded"))
    }
    photo.address?.let { add(DetailRow("Address", it)) }
    val captured = Instant.ofEpochMilli(photo.capturedAt)
    add(
        DetailRow(
            "Captured",
            CaptureTimeFormatter.formatDateTime(captured, settings.datePattern, settings.use24HourTime).orEmpty()
        )
    )
    photo.accuracy?.let { add(DetailRow("Accuracy", "±" + UnitConverter.formatDistance(it.toDouble(), settings.distanceUnit))) }
    photo.altitude?.let { add(DetailRow("Altitude", UnitConverter.formatDistance(it, settings.distanceUnit))) }
    photo.speed?.let { add(DetailRow("Speed", UnitConverter.formatSpeed(it.toDouble(), settings.speedUnit))) }
    photo.bearing?.let { add(DetailRow("Direction", CardinalDirection.format(it))) }
}
