package io.github.eduzyrtin.traccarwear.viewer.data

import android.content.Context
import androidx.wear.tiles.TileService
import io.github.eduzyrtin.traccarwear.viewer.api.TraccarApi
import io.github.eduzyrtin.traccarwear.viewer.api.UnauthorizedException
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
        loginWithToken(context, url, TraccarApi.requestToken(url, email.trim(), password))
    }

    // También sirve para quien entra con SSO/OpenID: genera el token en la web (Ajustes > Preferencias)
    suspend fun loginWithToken(context: Context, server: String, token: String) {
        val url = TraccarApi.normalizeUrl(server)
        val user = TraccarApi(url, token.trim()).user()
        ViewerStore.saveAccount(context, Account(url, token.trim(), user.name.ifBlank { user.email }))
        refresh(context)
        EventsWorker.schedule(context)
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
            Snapshot(devices.await(), positions.await(), System.currentTimeMillis())
        }
        ViewerStore.saveSnapshot(context, snapshot)
        if (updateTile) requestTileUpdate(context)
        return snapshot
    }

    fun requestTileUpdate(context: Context) {
        TileService.getUpdater(context).requestUpdate(DevicesTileService::class.java)
    }
}
