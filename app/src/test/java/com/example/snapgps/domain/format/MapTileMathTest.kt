package com.example.snapgps.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTileMathTest {

    @Test
    fun `null island is the centre of the world tile`() {
        val (x, y) = MapTileMath.worldPixel(0.0, 0.0, zoom = 0)
        assertEquals(128.0, x, 1e-9)
        assertEquals(128.0, y, 1e-9)
    }

    @Test
    fun `matches standard slippy map tile numbers`() {
        val (x, y) = MapTileMath.worldPixel(51.5074, -0.1278, zoom = 16)
        assertEquals(32744, (x / MapTileMath.TILE_SIZE).toInt())
        assertEquals(21792, (y / MapTileMath.TILE_SIZE).toInt())
    }

    @Test
    fun `pin sits at the centre of the frame`() {
        val frame = MapTileMath.frame(25.3176, 82.9739, zoom = 16, sizePx = 512)
        assertEquals(256f, frame.pinX, 1f)
        assertEquals(256f, frame.pinY, 1f)
    }

    @Test
    fun `tiles cover every pixel of the frame exactly once`() {
        val size = 512
        val frame = MapTileMath.frame(25.3176, 82.9739, zoom = 16, sizePx = size)
        val coverage = Array(size) { IntArray(size) }
        for (tile in frame.tiles) {
            for (py in maxOf(0, tile.top) until minOf(size, tile.top + MapTileMath.TILE_SIZE)) {
                for (px in maxOf(0, tile.left) until minOf(size, tile.left + MapTileMath.TILE_SIZE)) {
                    coverage[py][px]++
                }
            }
        }
        assertTrue(coverage.all { row -> row.all { it == 1 } })
        assertTrue(frame.tiles.size <= 9)
    }

    @Test
    fun `tiles wrap across the antimeridian`() {
        val zoom = 16
        val frame = MapTileMath.frame(0.0, 179.9999, zoom, sizePx = 512)
        val xs = frame.tiles.map { it.x }.toSet()
        assertTrue(0 in xs)
        assertTrue((1 shl zoom) - 1 in xs)
    }

    @Test
    fun `rows beyond the poles are skipped`() {
        val frame = MapTileMath.frame(89.9, 0.0, zoom = 2, sizePx = 512)
        assertTrue(frame.tiles.isNotEmpty())
        assertTrue(frame.tiles.all { it.y == 0 })
    }
}
