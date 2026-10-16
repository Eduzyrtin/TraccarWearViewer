package io.github.eduzyrtin.traccarwear.viewer.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import io.github.eduzyrtin.traccarwear.viewer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Descarga de mosaicos con caché en disco (cabeceras HTTP del proveedor) y en memoria. */
object TileLoader {
    private lateinit var client: OkHttpClient

    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun init(context: Context) {
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "map_tiles"), 50L * 1024 * 1024))
            // OSM y CARTO piden identificar la aplicación
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "TraccarWearViewer/${BuildConfig.VERSION_NAME} (Wear OS)")
                        .build(),
                )
            }
            .build()
    }

    fun cached(url: String): Bitmap? = memory.get(url)

    suspend fun load(url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) BitmapFactory.decodeStream(response.body.byteStream()) else null
                }
            } catch (_: IOException) {
                null
            }
        }
        bitmap?.let { memory.put(url, it) }
        return bitmap
    }
}
