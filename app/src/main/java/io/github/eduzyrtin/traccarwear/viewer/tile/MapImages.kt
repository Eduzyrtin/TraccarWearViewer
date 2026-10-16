package io.github.eduzyrtin.traccarwear.viewer.tile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Últimas imágenes de mapa del tile, en disco y nombradas por su versión de recursos
 * ("map-<hash>-<tamaño>"). El sistema puede pedir la misma versión varias veces o un rato después.
 */
object MapImages {
    const val NONE = "none"
    private const val KEEP = 8

    suspend fun save(context: Context, bytes: ByteArray, sizePx: Int): String = withContext(Dispatchers.IO) {
        val version = "map-${bytes.contentHashCode().toUInt()}-$sizePx"
        val dir = dir(context)
        File(dir, version).writeBytes(bytes)
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        version
    }

    fun load(context: Context, version: String): Pair<ByteArray, Int>? {
        val sizePx = version.substringAfterLast('-').toIntOrNull() ?: return null
        val file = File(dir(context), version)
        return if (file.exists()) file.readBytes() to sizePx else null
    }

    private fun dir(context: Context) = File(context.filesDir, "tile_maps").apply { mkdirs() }
}
