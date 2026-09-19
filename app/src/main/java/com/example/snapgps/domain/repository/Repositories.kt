package com.example.snapgps.domain.repository

import android.content.IntentSender
import android.graphics.Bitmap
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.FlashMode
import com.example.snapgps.domain.model.GpsLocation
import com.example.snapgps.domain.model.LensFacing
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.domain.model.ZoomInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface CameraRepository {
    /** The viewfinder surface for the currently bound camera, or null while unbound. */
    val surfaceRequest: StateFlow<SurfaceRequest?>
    val zoom: StateFlow<ZoomInfo>
    val hasFlashUnit: StateFlow<Boolean>

    suspend fun availableLenses(): Set<LensFacing>

    /** Binds preview + capture to [lifecycleOwner] and suspends until cancelled, then unbinds. */
    suspend fun bind(lifecycleOwner: LifecycleOwner, lens: LensFacing, flashMode: FlashMode)

    fun setFlashMode(mode: FlashMode)
    fun setZoomRatio(ratio: Float)

    /** Focus/meter at a point in the preview surface's coordinate space. */
    fun focusAt(surfaceX: Float, surfaceY: Float)

    /** A `Surface.ROTATION_*` value matching how the device is physically held. */
    fun setTargetRotation(rotation: Int)

    /** Captures a full-resolution JPEG into a temporary file owned by the caller. */
    suspend fun takePicture(): File
}

interface LocationRepository {
    fun locationUpdates(): Flow<GpsLocation>
    suspend fun getCurrentLocation(): GpsLocation?
    fun hasLocationPermission(): Boolean
    fun hasPreciseLocationPermission(): Boolean
    /** Emits whenever the system location toggle changes. */
    fun locationEnabled(): Flow<Boolean>
}

interface GeocodingRepository {
    suspend fun getAddress(latitude: Double, longitude: Double): String?
}

interface MapSnapshotRepository {
    /**
     * A square map centred on the coordinate with a pin on it, or null when the map tiles are
     * unavailable (e.g. offline with nothing cached). Each call returns a new bitmap the caller owns.
     */
    suspend fun snapshot(latitude: Double, longitude: Double): Bitmap?
}

interface HeadingRepository {
    /** Compass heading in degrees from magnetic north; empty flow when no sensor exists. */
    fun headingUpdates(): Flow<Float>

    /** Degrees to add to a magnetic heading to get a true-north heading at this position. */
    fun declination(latitude: Double, longitude: Double, altitudeM: Double, timeMs: Long): Float
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface PhotoProcessor {
    /** Burns the overlay into [source] and writes EXIF; returns a new temporary JPEG. */
    suspend fun process(source: File, metadata: PhotoMetadata, settings: AppSettings): File
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    /** The system must confirm deletion (e.g. the file predates a reinstall). */
    data class NeedsConsent(val intentSender: IntentSender) : DeleteResult
}

interface PhotoRepository {
    fun observePhotos(): Flow<List<Photo>>
    fun observePhoto(id: Long): Flow<Photo?>

    /** Publishes [processedFile] to MediaStore, records it, and deletes the file. */
    suspend fun save(processedFile: File, metadata: PhotoMetadata): Photo

    suspend fun delete(photo: Photo): DeleteResult

    /** Removes the app's record only, after the system has deleted the media itself. */
    suspend fun removeRecord(id: Long)

    /** Drops records whose image was deleted outside the app. */
    suspend fun pruneMissing()
}
