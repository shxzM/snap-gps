package com.example.snapgps.domain.format

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** A map tile and where its top-left corner lands in the output image. */
data class TilePlacement(val x: Int, val y: Int, val left: Int, val top: Int)

/** The tiles covering a square image centred on a coordinate, and the pin's pixel position. */
data class MapFrame(val zoom: Int, val tiles: List<TilePlacement>, val pinX: Float, val pinY: Float)

/** Web-Mercator ("slippy map") tile math, as used by OpenStreetMap and most tile servers. */
object MapTileMath {

    const val TILE_SIZE = 256
    const val MAX_LATITUDE = 85.05112878

    /** Global pixel position of a coordinate at [zoom]; x grows east, y grows south. */
    fun worldPixel(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> {
        val scale = TILE_SIZE.toDouble() * (1 shl zoom)
        val lat = Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        val x = (longitude + 180.0) / 360.0 * scale
        val y = (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * scale
        return x to y
    }

    /**
     * Tiles needed to fill a [sizePx] square centred on the coordinate. Tiles wrap around the
     * antimeridian; rows beyond the poles are left out.
     */
    fun frame(latitude: Double, longitude: Double, zoom: Int, sizePx: Int): MapFrame {
        val (cx, cy) = worldPixel(latitude, longitude, zoom)
        // Integer origin so adjacent tiles butt together without seams.
        val originX = floor(cx - sizePx / 2.0).toLong()
        val originY = floor(cy - sizePx / 2.0).toLong()
        val tilesPerSide = 1 shl zoom
        val tiles = mutableListOf<TilePlacement>()
        for (ty in tileRange(originY, sizePx)) {
            if (ty < 0 || ty >= tilesPerSide) continue
            for (tx in tileRange(originX, sizePx)) {
                tiles += TilePlacement(
                    x = Math.floorMod(tx, tilesPerSide),
                    y = ty,
                    left = (tx.toLong() * TILE_SIZE - originX).toInt(),
                    top = (ty.toLong() * TILE_SIZE - originY).toInt()
                )
            }
        }
        return MapFrame(zoom, tiles, (cx - originX).toFloat(), (cy - originY).toFloat())
    }

    private fun tileRange(origin: Long, sizePx: Int): IntRange {
        val first = floor(origin.toDouble() / TILE_SIZE).toInt()
        val last = ceil((origin + sizePx).toDouble() / TILE_SIZE).toInt() - 1
        return first..last
    }
}
