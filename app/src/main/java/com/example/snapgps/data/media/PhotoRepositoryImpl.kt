package com.example.snapgps.data.media

import androidx.core.net.toUri
import com.example.snapgps.data.local.PhotoDao
import com.example.snapgps.data.local.PhotoEntity
import com.example.snapgps.data.local.toDomain
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.domain.repository.DeleteResult
import com.example.snapgps.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PhotoRepositoryImpl(
    private val dao: PhotoDao,
    private val mediaStore: MediaStoreManager
) : PhotoRepository {

    override fun observePhotos(): Flow<List<Photo>> =
        dao.observeAll().map { list -> list.map(PhotoEntity::toDomain) }

    override fun observePhoto(id: Long): Flow<Photo?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun save(processedFile: File, metadata: PhotoMetadata): Photo {
        try {
            val displayName = "SnapGPS_" +
                FILE_TIMESTAMP.format(metadata.dateTime.atZone(ZoneId.systemDefault())) + ".jpg"
            val uri = mediaStore.save(processedFile, displayName, metadata.dateTime.toEpochMilli())
            val entity = PhotoEntity(
                uri = uri.toString(),
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                altitude = metadata.altitude,
                accuracy = metadata.accuracy,
                speed = metadata.speed,
                bearing = metadata.bearing,
                address = metadata.address,
                capturedAt = metadata.dateTime.toEpochMilli()
            )
            val id = dao.insert(entity)
            return entity.copy(id = id).toDomain()
        } finally {
            processedFile.delete()
        }
    }

    override suspend fun delete(photo: Photo): DeleteResult {
        val result = mediaStore.delete(photo.uri.toUri())
        if (result is DeleteResult.Deleted) dao.deleteById(photo.id)
        return result
    }

    override suspend fun removeRecord(id: Long) = dao.deleteById(id)

    override suspend fun pruneMissing() {
        val missing = dao.getAll().filterNot { mediaStore.exists(it.uri.toUri()) }.map { it.id }
        if (missing.isNotEmpty()) dao.deleteByIds(missing)
    }

    private companion object {
        val FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
    }
}
