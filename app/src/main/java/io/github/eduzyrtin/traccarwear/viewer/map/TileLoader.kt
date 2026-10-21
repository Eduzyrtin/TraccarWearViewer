package io.github.eduzyrtin.traccarwear.viewer.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import io.github.eduzyrtin.traccarwear.viewer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Descarga de mosaicos con caché en disco (cabeceras HTTP del proveedor) y en memoria. */
object TileLoader {
    private val KEY_PARAMETERS = listOf("key", "apiKey", "access_token")
    private lateinit var client: OkHttpClient

    // URL del servidor de Traccar: los proveedores con clave suelen restringirla por referer al dominio
    // del servidor (como hace la web de Traccar), así que se envía igual, y solo a los que llevan clave
    @Volatile
    var referer: String? = null

    fun setServer(serverUrl: String?) {
        referer = serverUrl?.toHttpUrlOrNull()?.resolve("/")?.toString()
    }

    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun init(context: Context) {
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "map_tiles"), 50L * 1024 * 1024))
            // El tile del reloj tiene poco tiempo para responder: mejor un mosaico que falta que un tile en blanco
            .callTimeout(5, TimeUnit.SECONDS)
            // OSM y CARTO piden identificar la aplicación
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                    .header("User-Agent", "TraccarWearViewer/${BuildConfig.VERSION_NAME} (Wear OS)")
                val usesKey = KEY_PARAMETERS.any { request.url.queryParameter(it) != null }
                referer?.takeIf { usesKey }?.let { builder.header("Referer", it) }
                chain.proceed(builder.build())
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
