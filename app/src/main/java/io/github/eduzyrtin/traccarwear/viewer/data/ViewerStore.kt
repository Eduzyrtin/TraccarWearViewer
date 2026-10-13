package io.github.eduzyrtin.traccarwear.viewer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.eduzyrtin.traccarwear.viewer.api.Device
import io.github.eduzyrtin.traccarwear.viewer.api.Position
import io.github.eduzyrtin.traccarwear.viewer.api.TraccarApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

data class Account(val serverUrl: String, val token: String, val userName: String)

/** Última foto de dispositivos y posiciones: la usan la lista y el tile sin esperar a la red. */
@Serializable
data class Snapshot(val devices: List<Device>, val positions: List<Position>, val updatedAt: Long) {
    fun positionOf(deviceId: Long): Position? = positions.find { it.deviceId == deviceId }
}

private val Context.dataStore by preferencesDataStore("viewer")

object ViewerStore {
    private val SERVER_URL = stringPreferencesKey("server_url")
    private val TOKEN = stringPreferencesKey("token")
    private val USER_NAME = stringPreferencesKey("user_name")
    private val SNAPSHOT = stringPreferencesKey("snapshot")
    private val FAVORITES = stringSetPreferencesKey("favorites")
    private val NOTIFICATIONS = booleanPreferencesKey("notifications")
    private val LAST_EVENT_CHECK = longPreferencesKey("last_event_check")

    fun account(context: Context): Flow<Account?> = context.dataStore.data.map { prefs ->
        val url = prefs[SERVER_URL]
        val token = prefs[TOKEN]
        if (url != null && token != null) Account(url, token, prefs[USER_NAME] ?: "") else null
    }

    suspend fun saveAccount(context: Context, account: Account) {
        context.dataStore.edit {
            it[SERVER_URL] = account.serverUrl
            it[TOKEN] = account.token
            it[USER_NAME] = account.userName
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

    fun notificationsEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[NOTIFICATIONS] ?: true }

    suspend fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    suspend fun lastEventCheck(context: Context): Long? = context.dataStore.data.first()[LAST_EVENT_CHECK]

    suspend fun setLastEventCheck(context: Context, time: Long) {
        context.dataStore.edit { it[LAST_EVENT_CHECK] = time }
    }
}
