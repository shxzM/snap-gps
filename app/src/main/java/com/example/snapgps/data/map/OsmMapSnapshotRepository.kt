package com.example.snapgps.data.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import com.example.snapgps.domain.format.MapTileMath
import com.example.snapgps.domain.repository.MapSnapshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Map thumbnails stitched from OpenStreetMap tiles. Tiles are cached on disk, so once an area has
 * been seen (e.g. in the viewfinder) stamping a photo there needs no network. Every failure yields
 * null so the photo is never blocked on the map.
 *
 * OSM tile usage policy: identify the app in the User-Agent, show attribution, cache tiles and keep
 * traffic light. Swap [TILE_SERVER] for a commercial provider if usage grows.
 */
class OsmMapSnapshotRepository(context: Context) : MapSnapshotRepository {

    private val tileDir = File(context.cacheDir, "map_tiles")
    private val userAgent = "SnapGPS/1.0 (Android; ${context.packageName})"

    /** After a failed download, serve only cached tiles for a while instead of timing out per tile. */
    @Volatile private var lastFailureMs = 0L

    override suspend fun snapshot(latitude: Double, longitude: Double): Bitmap? = withContext(Dispatchers.IO) {
        val frame = MapTileMath.frame(latitude, longitude, ZOOM, SIZE_PX)
        val output = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(BACKGROUND)
            for (placement in frame.tiles) {
                ensureActive()
                // A patchy map looks broken; better no map at all.
                val tile = loadTile(placement.x, placement.y) ?: run {
                    output.recycle()
                    return@withContext null
                }
                canvas.drawBitmap(tile, placement.left.toFloat(), placement.top.toFloat(), null)
                tile.recycle()
            }
            drawPin(canvas, frame.pinX, frame.pinY)
            drawAttribution(canvas)
            output
        } catch (e: Throwable) {
            output.recycle()
            throw e
        }
    }

    private fun loadTile(x: Int, y: Int): Bitmap? {
        val file = File(tileDir, "${ZOOM}_${x}_$y.png")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_TTL_MS
        if (!fresh && System.currentTimeMillis() - lastFailureMs > FAILURE_BACKOFF_MS) {
            if (!download(x, y, file)) lastFailureMs = System.currentTimeMillis()
        }
        // A stale tile beats none when offline.
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    private fun download(x: Int, y: Int, target: File): Boolean {
        var part: File? = null
        return try {
            val connection = URL("$TILE_SERVER/$ZOOM/$x/$y.png").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", userAgent)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
                tileDir.mkdirs()
                val tmp = File.createTempFile("tile", ".part", tileDir).also { part = it }
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(target)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            Log.d(TAG, "Map tile unavailable")
            false
        } finally {
            part?.delete()
        }
    }

    /** Classic teardrop marker with its tip on the location. */
    private fun drawPin(canvas: Canvas, x: Float, tipY: Float) {
        val height = SIZE_PX * 0.13f
        val radius = height * 0.34f
        val headY = tipY - height + radius

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
        canvas.drawOval(RectF(x - radius * 0.6f, tipY - radius * 0.2f, x + radius * 0.6f, tipY + radius * 0.2f), shadow)

        val head = Path().apply { addCircle(x, headY, radius, Path.Direction.CW) }
        val point = Path().apply {
            moveTo(x - radius * 0.87f, headY + radius * 0.5f)
            lineTo(x, tipY)
            lineTo(x + radius * 0.87f, headY + radius * 0.5f)
            close()
        }
        head.op(point, Path.Op.UNION)

        canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PIN_COLOR })
        canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.16f
            color = Color.WHITE
        })
        canvas.drawCircle(x, headY, radius * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private fun drawAttribution(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 40, 40, 40)
            textSize = SIZE_PX * 0.036f
        }
        val pad = paint.textSize * 0.3f
        val width = paint.measureText(ATTRIBUTION)
        val bottom = SIZE_PX.toFloat()
        val right = SIZE_PX.toFloat()
        val background = Paint().apply { color = Color.argb(190, 255, 255, 255) }
        canvas.drawRect(right - width - 2 * pad, bottom - paint.textSize - 2 * pad, right, bottom, background)
        canvas.drawText(ATTRIBUTION, right - width - pad, bottom - pad - paint.descent(), paint)
    }

    private companion object {
        const val TAG = "MapSnapshot"
        const val TILE_SERVER = "https://tile.openstreetmap.org"
        const val ATTRIBUTION = "© OpenStreetMap"
        /** Street level: roughly 1.2 km across at the equator for [SIZE_PX]. */
        const val ZOOM = 16
        const val SIZE_PX = 512
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_TIMEOUT_MS = 4_000
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1_000
        const val FAILURE_BACKOFF_MS = 15_000L
        val BACKGROUND = Color.rgb(242, 239, 233)
        val PIN_COLOR = Color.rgb(229, 57, 53)
    }
}
