package io.github.eduzyrtin.traccarwear.viewer.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.ByteArrayOutputStream

/** Pinta el mapa centrado en un punto, con su marcador, como imagen (para el tile). */
object MapBitmapRenderer {
    private const val BACKGROUND = 0xFFE8E6E1.toInt()

    suspend fun render(
        style: MapStyle,
        latitude: Double,
        longitude: Double,
        zoom: Int,
        sizePx: Int,
        density: Float,
        markerColor: Int,
    ): Result {
        val tilePx = 256.0 * density
        val world = WebMercator.worldSize(zoom, tilePx)
        val centerX = WebMercator.x(longitude, world)
        val centerY = WebMercator.y(latitude, world)
        val tiles = WebMercator.visibleTiles(centerX, centerY, zoom, tilePx, sizePx.toDouble(), sizePx.toDouble())
        val bitmaps = coroutineScope {
            tiles.map { async { TileLoader.load(style.tileUrl(zoom, it.x, it.y)) } }.awaitAll()
        }

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        tiles.zip(bitmaps).forEach { (tile, image) ->
            image ?: return@forEach
            val left = tile.left.toFloat()
            val top = tile.top.toFloat()
            canvas.drawBitmap(image, null, RectF(left, top, left + tilePx.toFloat(), top + tilePx.toFloat()), tilePaint)
        }

        val center = sizePx / 2f
        val radius = 9 * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x55000000
        canvas.drawCircle(center, center + density, radius + 3 * density, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(center, center, radius + 3 * density, paint)
        paint.color = markerColor
        canvas.drawCircle(center, center, radius, paint)

        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            out.toByteArray()
        }
        return Result(bytes, complete = bitmaps.all { it != null })
    }

    /** [complete] es falso si algún mosaico no se pudo descargar (el fondo liso ocupa su sitio). */
    class Result(val bytes: ByteArray, val complete: Boolean)
}
