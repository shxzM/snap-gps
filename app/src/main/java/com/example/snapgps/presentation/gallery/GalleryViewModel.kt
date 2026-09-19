package com.example.snapgps.presentation.gallery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.repository.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(private val photoRepository: PhotoRepository) : ViewModel() {

    /** `null` while loading, so the empty state doesn't flash before the first query returns. */
    val photos: StateFlow<List<Photo>?> = photoRepository.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            try {
                photoRepository.pruneMissing()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("GalleryViewModel", "Pruning missing photos failed", e)
            }
        }
    }
}
