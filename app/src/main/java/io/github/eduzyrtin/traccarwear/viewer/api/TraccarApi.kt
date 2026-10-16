package io.github.eduzyrtin.traccarwear.viewer.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import io.github.eduzyrtin.traccarwear.viewer.map.MapType
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

// Solo los campos que usa la app; el resto se ignora (ignoreUnknownKeys)

@Serializable
data class User(
    val id: Long,
    val name: String = "",
    val email: String = "",
    val attributes: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Device(
    val id: Long,
    val name: String,
    val status: String? = null,
    val lastUpdate: String? = null,
    val positionId: Long = 0,
    val disabled: Boolean = false,
)

@Serializable
data class Position(
    val id: Long = 0,
    val deviceId: Long,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // En nudos, como lo guarda Traccar
    val speed: Double = 0.0,
    val fixTime: String? = null,
    val address: String? = null,
)

@Serializable
data class Event(
    val id: Long,
    val type: String,
    val eventTime: String? = null,
    val deviceId: Long = 0,
    val geofenceId: Long = 0,
    val attributes: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class NotificationRule(val id: Long, val type: String)

@Serializable
data class Geofence(val id: Long, val name: String)

@Serializable
data class ServerInfo(
    val map: String? = null,
    val mapUrl: String? = null,
    val attributes: JsonObject = JsonObject(emptyMap()),
)

// Claves de mapas presentes en unas preferencias de Traccar (mismos nombres que en la web)
fun JsonObject.mapKeys(): Map<String, String> = MapType.KEY_ATTRIBUTES.mapNotNull { name ->
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { name to it }
}.toMap()

class UnauthorizedException : IOException("Unauthorized")

class HttpException(val code: Int) : IOException("HTTP $code")

/** API REST de Traccar 6 autenticada con token (Authorization: Bearer). */
class TraccarApi(val serverUrl: String, private val token: String) {

    suspend fun server(): ServerInfo = get("api/server")

    // GET /api/session (sin id) no admite token Bearer; con id sí
    suspend fun user(id: Long): User = get("api/session/$id")

    suspend fun devices(): List<Device> = get("api/devices")

    // Última posición de cada dispositivo
    suspend fun positions(): List<Position> = get("api/positions")

    suspend fun geofences(): List<Geofence> = get("api/geofences")

    // Las notificaciones que el usuario tiene configuradas en el servidor
    suspend fun notificationRules(): List<NotificationRule> = get("api/notifications")

    suspend fun events(deviceIds: Collection<Long>, types: Collection<String>, from: Instant, to: Instant): List<Event> =
        get("api/reports/events") {
            deviceIds.forEach { addQueryParameter("deviceId", it.toString()) }
            types.forEach { addQueryParameter("type", it) }
            addQueryParameter("from", from.toString())
            addQueryParameter("to", to.toString())
        }

    suspend fun revokeToken() {
        val body = FormBody.Builder().add("token", token).build()
        execute(Request.Builder().url(url("api/session/token/revoke")).post(body).build())
    }

    private suspend inline fun <reified T> get(path: String, noinline query: HttpUrl.Builder.() -> Unit = {}): T {
        val request = Request.Builder().url(url(path, query)).header("Accept", "application/json").build()
        return json.decodeFromString(execute(request))
    }

    private fun url(path: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        serverUrl.toHttpUrl().newBuilder().addPathSegments(path).apply(query).build()

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val authorized = request.newBuilder().header("Authorization", "Bearer $token").build()
        client.newCall(authorized).execute().use { response ->
            when {
                response.code == 401 -> throw UnauthorizedException()
                !response.isSuccessful -> throw HttpException(response.code)
                else -> response.body.string()
            }
        }
    }

    companion object {
        private const val TOKEN_DAYS = 365L

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            return if (hasScheme) trimmed else "https://$trimmed"
        }

        /**
         * Abre una sesión con correo y contraseña solo para pedir un token de larga duración.
         * La contraseña no se guarda en el reloj.
         */
        suspend fun requestToken(serverUrl: String, email: String, password: String): Pair<String, User> =
            withContext(Dispatchers.IO) {
                val loginClient = client.newBuilder().cookieJar(MemoryCookieJar()).build()
                val base = serverUrl.toHttpUrl()
                val login = Request.Builder()
                    .url(base.newBuilder().addPathSegments("api/session").build())
                    .post(FormBody.Builder().add("email", email).add("password", password).build())
                    .build()
                val user = loginClient.newCall(login).execute().use { response ->
                    if (response.code == 401) throw UnauthorizedException()
                    if (!response.isSuccessful) throw HttpException(response.code)
                    json.decodeFromString<User>(response.body.string())
                }
                val expiration = Instant.now().plus(TOKEN_DAYS, ChronoUnit.DAYS).toString()
                val tokenRequest = Request.Builder()
                    .url(base.newBuilder().addPathSegments("api/session/token").build())
                    .post(FormBody.Builder().add("expiration", expiration).build())
                    .build()
                loginClient.newCall(tokenRequest).execute().use { response ->
                    if (!response.isSuccessful) throw HttpException(response.code)
                    response.body.string().trim()
                }.let { it to user }
            }
    }
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old -> cookies.any { it.name == old.name } }
        this.cookies.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
}
