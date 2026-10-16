package io.github.eduzyrtin.traccarwear.viewer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.eduzyrtin.traccarwear.viewer.api.Device
import io.github.eduzyrtin.traccarwear.viewer.api.Position
import io.github.eduzyrtin.traccarwear.viewer.api.TraccarApi
import io.github.eduzyrtin.traccarwear.viewer.map.MapStyle
import io.github.eduzyrtin.traccarwear.viewer.map.MapType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

data class Account(
    val serverUrl: String,
    val token: String,
    val userName: String,
    // Para leer las preferencias del usuario (claves de mapas). Null en sesiones con token pegado.
    val userId: Long? = null,
)

// Cuándo muestra el reloj sus propios avisos de eventos del servidor
enum class NotificationMode { OFF, AWAY_FROM_PHONE, ALWAYS }

/** Última foto de dispositivos y posiciones: la usan la lista y el tile sin esperar a la red. */
@Serializable
data class Snapshot(
    val devices: List<Device>,
    val positions: List<Position>,
    val updatedAt: Long,
    // Mapa configurado en el servidor (ver MapStyle)
    val map: String? = null,
    val mapUrl: String? = null,
    // Claves de mapas de las preferencias de Traccar (usuario y servidor)
    val mapKeys: Map<String, String> = emptyMap(),
) {
    fun positionOf(deviceId: Long): Position? = positions.find { it.deviceId == deviceId }

    fun mapStyle(choice: String?): MapStyle = MapStyle.resolve(choice, map, mapUrl, mapKeys)
}

private val Context.dataStore by preferencesDataStore("viewer")

object ViewerStore {
    private val SERVER_URL = stringPreferencesKey("server_url")
    private val TOKEN = stringPreferencesKey("token")
    private val USER_NAME = stringPreferencesKey("user_name")
    private val SNAPSHOT = stringPreferencesKey("snapshot")
    private val FAVORITES = stringSetPreferencesKey("favorites")
    private val USER_ID = longPreferencesKey("user_id")
    private val NOTIFICATION_MODE = stringPreferencesKey("notification_mode")
    private val MAP_CHOICE = stringPreferencesKey("map_choice")
    private val AUTO_HIDE_CONTROLS = booleanPreferencesKey("auto_hide_map_controls")
    private val LAST_EVENT_CHECK = longPreferencesKey("last_event_check")

    fun account(context: Context): Flow<Account?> = context.dataStore.data.map { prefs ->
        val url = prefs[SERVER_URL]
        val token = prefs[TOKEN]
        if (url != null && token != null) Account(url, token, prefs[USER_NAME] ?: "", prefs[USER_ID]) else null
    }

    suspend fun saveAccount(context: Context, account: Account) {
        context.dataStore.edit {
            it[SERVER_URL] = account.serverUrl
            it[TOKEN] = account.token
            it[USER_NAME] = account.userName
            account.userId?.let { id -> it[USER_ID] = id }
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }

    fun snapshot(context: Context): Flow<Snapshot?> = context.dataStore.data.map { prefs ->
        prefs[SNAPSHOT]?.let { runCatching { TraccarApi.json.decodeFromString<Snapshot>(it) }.getOrNull() }
    }

    suspend fun saveSnapshot(context: Context, snapshot: Snapshot) {
        context.dataStore.edit { it[SNAPSHOT] = TraccarApi.json.encodeToString(snapshot) }
    }

    fun favorites(context: Context): Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[FAVORITES].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun toggleFavorite(context: Context, deviceId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES].orEmpty()
            val id = deviceId.toString()
            prefs[FAVORITES] = if (id in current) current - id else current + id
        }
    }

    // Desactivados por defecto: quien los quiera los activa en Ajustes
    fun notificationMode(context: Context): Flow<NotificationMode> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATION_MODE]?.let { name -> NotificationMode.entries.find { it.name == name } } ?: NotificationMode.OFF
    }

    suspend fun setNotificationMode(context: Context, mode: NotificationMode) {
        context.dataStore.edit { it[NOTIFICATION_MODE] = mode.name }
    }

    // Botones +/− y ≡ del mapa: por defecto se ocultan tras unos segundos sin tocar la pantalla
    fun autoHideMapControls(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[AUTO_HIDE_CONTROLS] ?: true }

    suspend fun setAutoHideMapControls(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[AUTO_HIDE_CONTROLS] = enabled }
    }

    // Id de MapType elegido por el usuario; por defecto, el del servidor
    fun mapChoice(context: Context): Flow<String> =
        context.dataStore.data.map { it[MAP_CHOICE] ?: MapType.SERVER.id }

    suspend fun setMapChoice(context: Context, id: String) {
        context.dataStore.edit { it[MAP_CHOICE] = id }
    }

    // Cada tile añadido tiene su propio id: guarda qué dispositivo muestra y con qué zoom
    suspend fun tileDevice(context: Context, tileId: Int): Long? =
        context.dataStore.data.first()[longPreferencesKey("tile_device_$tileId")]

    suspend fun setTileDevice(context: Context, tileId: Int, deviceId: Long) {
        context.dataStore.edit { it[longPreferencesKey("tile_device_$tileId")] = deviceId }
    }

    suspend fun tileZoom(context: Context, tileId: Int): Int? =
        context.dataStore.data.first()[intPreferencesKey("tile_zoom_$tileId")]

    suspend fun setTileZoom(context: Context, tileId: Int, zoom: Int) {
        context.dataStore.edit { it[intPreferencesKey("tile_zoom_$tileId")] = zoom }
    }

    suspend fun lastEventCheck(context: Context): Long? = context.dataStore.data.first()[LAST_EVENT_CHECK]

    suspend fun setLastEventCheck(context: Context, time: Long) {
        context.dataStore.edit { it[LAST_EVENT_CHECK] = time }
    }
}
