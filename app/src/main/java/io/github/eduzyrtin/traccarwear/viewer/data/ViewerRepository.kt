package io.github.eduzyrtin.traccarwear.viewer.data

import android.content.Context
import androidx.core.net.toUri
import androidx.wear.tiles.TileService
import io.github.eduzyrtin.traccarwear.viewer.api.TraccarApi
import io.github.eduzyrtin.traccarwear.viewer.api.UnauthorizedException
import io.github.eduzyrtin.traccarwear.viewer.api.mapKeys
import io.github.eduzyrtin.traccarwear.viewer.notify.EventsWorker
import io.github.eduzyrtin.traccarwear.viewer.tile.DevicesTileService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

object ViewerRepository {

    suspend fun api(context: Context): TraccarApi? =
        ViewerStore.account(context).first()?.let { TraccarApi(it.serverUrl, it.token) }

    suspend fun login(context: Context, server: String, email: String, password: String) {
        val url = TraccarApi.normalizeUrl(server)
        val (token, user) = TraccarApi.requestToken(url, email.trim(), password)
        saveAndLoad(context, Account(url, token, user.name.ifBlank { user.email }, user.id))
    }

    // También sirve para quien entra con SSO/OpenID: genera el token en la web (Ajustes > Preferencias).
    // Traccar no deja consultar el usuario con un token Bearer, así que se valida pidiendo los dispositivos.
    suspend fun loginWithToken(context: Context, server: String, token: String) {
        val url = TraccarApi.normalizeUrl(server)
        saveAndLoad(context, Account(url, token.trim(), url.toUri().host.orEmpty()))
    }

    private suspend fun saveAndLoad(context: Context, account: Account) {
        ViewerStore.saveAccount(context, account)
        try {
            refresh(context)
        } catch (e: Exception) {
            ViewerStore.clear(context)
            throw e
        }
        if (ViewerStore.notificationMode(context).first() != NotificationMode.OFF) EventsWorker.schedule(context)
    }

    suspend fun logout(context: Context) {
        try {
            api(context)?.revokeToken()
        } catch (_: Exception) {
            // Sin conexión o token ya caducado: se borra igualmente en el reloj
        }
        ViewerStore.clear(context)
        EventsWorker.cancel(context)
        requestTileUpdate(context)
    }

    suspend fun refresh(context: Context, updateTile: Boolean = true): Snapshot {
        val api = api(context) ?: throw UnauthorizedException()
        val snapshot = coroutineScope {
            val devices = async { api.devices() }
            val positions = async { api.positions() }
            val server = async { api.server() }
            val userId = ViewerStore.account(context).first()?.userId
            val user = async { userId?.let { runCatching { api.user(it) }.getOrNull() } }
            Snapshot(
                devices.await(),
                positions.await(),
                System.currentTimeMillis(),
                map = server.await().map,
                mapUrl = server.await().mapUrl,
                // Como la web de Traccar: primero las del usuario, si no, las del servidor
                mapKeys = server.await().attributes.mapKeys() + user.await()?.attributes?.mapKeys().orEmpty(),
            )
        }
        ViewerStore.saveSnapshot(context, snapshot)
        if (updateTile) requestTileUpdate(context)
        return snapshot
    }

    fun requestTileUpdate(context: Context) {
        TileService.getUpdater(context).requestUpdate(DevicesTileService::class.java)
    }
}
