package com.example.snapgps.data.media

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.snapgps.domain.repository.DeleteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Publishes photos to shared storage under Pictures/SnapGPS (TDD §17). */
class MediaStoreManager(context: Context) {

    private val resolver = context.contentResolver

    suspend fun save(file: File, displayName: String, dateTakenMs: Long): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMs)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Could not create gallery entry")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("Could not open gallery entry")
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    suspend fun delete(uri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        try {
            resolver.delete(uri, null, null)
            DeleteResult.Deleted
        } catch (e: RecoverableSecurityException) {
            // We no longer own the file (e.g. after reinstall): ask the system to confirm.
            DeleteResult.NeedsConsent(MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender)
        }
    }

    suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    companion object {
        const val ALBUM = "SnapGPS"
    }
}
