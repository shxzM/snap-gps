package com.example.snapgps.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.snapgps.domain.format.OverlayContentBuilder
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.domain.repository.PhotoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/**
 * decode → orient → burn overlay → encode → EXIF, all off the main thread (TDD §15).
 * Holds at most one full-size bitmap at a time, except briefly while rotating.
 */
class ImageProcessor(
    private val tempFiles: TempFiles,
    private val renderer: OverlayBitmapRenderer,
    private val exifWriter: ExifWriter
) : PhotoProcessor {

    override suspend fun process(source: File, metadata: PhotoMetadata, settings: AppSettings): File =
        withContext(Dispatchers.Default) {
            val lines = OverlayContentBuilder.build(metadata, settings)
            val orientation = ExifInterface(source)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            var bitmap = decode(source)
            val output = tempFiles.newJpeg("final_")
            try {
                bitmap = applyOrientation(bitmap, orientation)
                renderer.draw(Canvas(bitmap), bitmap.width, bitmap.height, lines, settings.overlay)
                FileOutputStream(output).use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                        throw IOException("JPEG encoding failed")
                    }
                }
            } catch (e: Throwable) {
                output.delete()
                throw e
            } finally {
                bitmap.recycle()
            }
            try {
                exifWriter.write(output, source, metadata, settings.embedGpsMetadata)
            } catch (e: Throwable) {
                output.delete()
                throw e
            }
            Log.d(TAG, "Photo processed")
            output
        }

    /** Decodes mutable, downsampled so the long edge fits [MAX_EDGE_PX]; halves again on OOM. */
    private fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Captured image is unreadable")

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_EDGE_PX) sampleSize *= 2

        repeat(MAX_DECODE_ATTEMPTS) {
            try {
                val options = BitmapFactory.Options().apply {
                    inMutable = true
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeFile(file.absolutePath, options)
                    ?: throw IOException("Captured image could not be decoded")
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Decode ran out of memory at sample size $sampleSize")
                sampleSize *= 2
            }
        }
        throw IOException("Not enough memory to process the photo")
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        var rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        if (!rotated.isMutable) {
            val mutable = rotated.copy(Bitmap.Config.ARGB_8888, true)
            rotated.recycle()
            rotated = mutable
        }
        return rotated
    }

    private companion object {
        const val TAG = "ImageProcessor"
        const val MAX_EDGE_PX = 4096
        const val JPEG_QUALITY = 92
        const val MAX_DECODE_ATTEMPTS = 3
    }
}
