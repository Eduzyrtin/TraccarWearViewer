package io.github.eduzyrtin.traccarwear.viewer.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/** Proyección Web Mercator en píxeles de "mundo" para un zoom y un tamaño de mosaico dados. */
object WebMercator {
    private const val MAX_LATITUDE = 85.05112878

    fun worldSize(zoom: Int, tilePx: Double): Double = tilePx * 2.0.pow(zoom)

    fun x(longitude: Double, worldSize: Double): Double = (longitude + 180.0) / 360.0 * worldSize

    fun y(latitude: Double, worldSize: Double): Double {
        val s = sin(Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * worldSize
    }

    fun longitude(x: Double, worldSize: Double): Double = x / worldSize * 360.0 - 180.0

    fun latitude(y: Double, worldSize: Double): Double =
        Math.toDegrees(atan(sinh(PI * (1 - 2 * y / worldSize))))

    data class VisibleTile(val x: Int, val y: Int, val left: Double, val top: Double)

    /** Mosaicos que cubren un rectángulo de [width]×[height] centrado en ([centerX], [centerY]). */
    fun visibleTiles(centerX: Double, centerY: Double, zoom: Int, tilePx: Double, width: Double, height: Double): List<VisibleTile> {
        val count = 1 shl zoom
        val left = centerX - width / 2
        val top = centerY - height / 2
        val firstX = floor(left / tilePx).toInt()
        val lastX = floor((left + width) / tilePx).toInt()
        val firstY = floor(top / tilePx).toInt().coerceAtLeast(0)
        val lastY = floor((top + height) / tilePx).toInt().coerceAtMost(count - 1)
        val tiles = mutableListOf<VisibleTile>()
        for (ty in firstY..lastY) {
            for (tx in firstX..lastX) {
                tiles += VisibleTile(Math.floorMod(tx, count), ty, tx * tilePx - left, ty * tilePx - top)
            }
        }
        return tiles
    }
}
