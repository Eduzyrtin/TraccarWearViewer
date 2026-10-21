package io.github.eduzyrtin.traccarwear.viewer.tile

import android.content.Context
import android.text.format.DateUtils
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import io.github.eduzyrtin.traccarwear.viewer.MainActivity
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.TilePickerActivity
import io.github.eduzyrtin.traccarwear.viewer.api.Device
import io.github.eduzyrtin.traccarwear.viewer.data.Snapshot
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerRepository
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerStore
import io.github.eduzyrtin.traccarwear.viewer.map.MapBitmapRenderer
import io.github.eduzyrtin.traccarwear.viewer.map.MapStyle
import io.github.eduzyrtin.traccarwear.viewer.map.TileLoader
import io.github.eduzyrtin.traccarwear.viewer.ui.parseTime
import io.github.eduzyrtin.traccarwear.viewer.ui.statusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tile con el mapa de un dispositivo (cada tile añadido puede mostrar uno distinto).
 * Botones +/− para el zoom; tocar el nombre cambia de dispositivo; tocar la hora actualiza;
 * tocar el mapa abre la app centrada en ese dispositivo.
 */
class DevicesTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Main) { buildTile(requestParams) }

    // La imagen del mapa se guarda en disco por versión y se sirve todas las veces que el sistema la pida
    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
        val resources = ResourceBuilders.Resources.Builder().setVersion(requestParams.version)
        MapImages.load(this@DevicesTileService, requestParams.version)?.let { (bytes, sizePx) ->
            resources.addIdToImageMapping(MAP_RESOURCE_ID, imageResource(bytes, sizePx))
        }
        resources.build()
    }

    // Al aparecer el tile en pantalla se pide actualizarlo: así enseña la última posición
    override fun onRecentInteractionEventsAsync(
        events: List<EventBuilders.TileInteractionEvent>,
    ): ListenableFuture<Void> {
        if (events.any { it.eventType == EventBuilders.TileInteractionEvent.ENTER }) {
            getUpdater(this).requestUpdate(DevicesTileService::class.java)
        }
        return super.onRecentInteractionEventsAsync(events)
    }

    private suspend fun buildTile(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val context: Context = this
        val tileId = requestParams.tileId
        val clicked = requestParams.currentState.lastClickableId
        val deviceConfiguration = requestParams.deviceConfiguration
        var resourcesVersion = MapImages.NONE
        // El intervalo de refresco elegido en Ajustes
        var freshness = ViewerStore.refreshInterval(context).first().minutes * 60_000L

        val account = ViewerStore.account(context).first()
        // El servicio puede arrancar antes de que la aplicación lo haya fijado
        TileLoader.setServer(account?.serverUrl)

        val layout = if (account == null) {
            materialScope(context, deviceConfiguration) {
                message(context.getString(R.string.tile_sign_in), openApp(context, null))
            }
        } else {
            val snapshot = freshSnapshot(context, force = clicked == REFRESH_ID)
            val device = snapshot?.let { pickDevice(context, tileId, it) }
            val position = device?.let { snapshot.positionOf(it.id) }
            if (snapshot == null || device == null || position == null) {
                materialScope(context, deviceConfiguration) {
                    message(context.getString(R.string.no_positions), openPicker(context, tileId))
                }
            } else {
                val style = snapshot.mapStyle(ViewerStore.mapChoice(context).first())
                val zoom = when (clicked) {
                    ZOOM_IN_ID -> (ViewerStore.tileZoom(context, tileId) ?: DEFAULT_ZOOM) + 1
                    ZOOM_OUT_ID -> (ViewerStore.tileZoom(context, tileId) ?: DEFAULT_ZOOM) - 1
                    else -> ViewerStore.tileZoom(context, tileId) ?: DEFAULT_ZOOM
                }.coerceIn(MapStyle.MIN_ZOOM, style.maxZoom)
                ViewerStore.setTileZoom(context, tileId, zoom)

                val density = deviceConfiguration.screenDensity
                val sizePx = (deviceConfiguration.screenWidthDp * density).toInt()
                val image = MapBitmapRenderer.render(
                    style, position.latitude, position.longitude, zoom, sizePx, density, statusColor(device.status),
                )
                resourcesVersion = MapImages.save(context, image.bytes, sizePx)
                // Si faltan mosaicos (sin red a tiempo), se vuelve a intentar pronto
                if (!image.complete) freshness = RETRY_MILLIS
                materialScope(context, deviceConfiguration) { mapLayout(context, tileId, device) }
            }
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersion)
            .setFreshnessIntervalMillis(freshness)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    private suspend fun freshSnapshot(context: Context, force: Boolean): Snapshot? {
        val cached = ViewerStore.snapshot(context).first()
        if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < MIN_REFRESH_MILLIS) return cached
        return try {
            withTimeout(REFRESH_TIMEOUT_MILLIS) { ViewerRepository.refresh(context, updateTile = false) }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            cached
        }
    }

    // El elegido para este tile; si no hay ninguno, el primer favorito o el último activo
    private suspend fun pickDevice(context: Context, tileId: Int, snapshot: Snapshot): Device? {
        val chosen = ViewerStore.tileDevice(context, tileId)
        snapshot.devices.find { it.id == chosen }?.let { return it }
        val favorites = ViewerStore.favorites(context).first()
        return snapshot.devices.filter { snapshot.positionOf(it.id) != null }
            .sortedWith(compareByDescending<Device> { it.id in favorites }.thenByDescending { parseTime(it.lastUpdate) ?: 0L })
            .firstOrNull()
    }

    private fun MaterialScope.mapLayout(context: Context, tileId: Int, device: Device): LayoutElement {
        val ago = parseTime(device.lastUpdate)?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        } ?: "—"
        // El constructor sin ProtoLayoutScope y setResourceId están marcados como obsoletos, pero siguen siendo
        // los que casan con onTileResourcesRequest (imagen por versión en disco). Migrar a ProtoLayoutScope
        // exige rehacer entero el flujo de recursos del tile.
        @Suppress("DEPRECATION")
        val map = LayoutElementBuilders.Image.Builder()
            .setResourceId(MAP_RESOURCE_ID)
            .setWidth(expand())
            .setHeight(expand())
            .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(layer(map, clickable = openApp(context, device.id)))
            .addContent(
                layer(
                    LayoutElementBuilders.Row.Builder()
                        .setWidth(expand())
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                        .addContent(zoomButton("−", ZOOM_OUT_ID))
                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(expand()).build())
                        .addContent(zoomButton("+", ZOOM_IN_ID))
                        .build(),
                    padding = 6f,
                ),
            )
            .addContent(
                layer(
                    pill(device.name, openPicker(context, tileId)),
                    verticalAlignment = LayoutElementBuilders.VERTICAL_ALIGN_TOP,
                    padding = 18f,
                ),
            )
            .addContent(
                layer(
                    pill(ago.toString(), reload(REFRESH_ID)),
                    verticalAlignment = LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM,
                    padding = 18f,
                ),
            )
            .build()
    }

    private fun MaterialScope.zoomButton(label: String, id: String): LayoutElement = textButton(
        onClick = reload(id),
        labelContent = { text(label.layoutString, typography = Typography.TITLE_LARGE) },
        width = dp(40f),
        height = dp(40f),
    )

    // Etiqueta de texto sobre el mapa, legible con cualquier fondo
    private fun MaterialScope.pill(value: String, clickable: Clickable): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xCC000000.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(12f)).build())
                            .build(),
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(10f)).setEnd(dp(10f)).setTop(dp(3f)).setBottom(dp(3f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(text(value.layoutString, typography = Typography.LABEL_MEDIUM, maxLines = 1))
            .build()

    // Capa a pantalla completa que coloca su contenido arriba, en el centro o abajo
    private fun layer(
        content: LayoutElement,
        verticalAlignment: Int = LayoutElementBuilders.VERTICAL_ALIGN_CENTER,
        padding: Float = 0f,
        clickable: Clickable? = null,
    ): LayoutElement {
        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(padding)).build())
        clickable?.let { modifiers.setClickable(it) }
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(verticalAlignment)
            .setModifiers(modifiers.build())
            .addContent(content)
            .build()
    }

    private fun MaterialScope.message(value: String, clickable: Clickable): LayoutElement = primaryLayout(
        mainSlot = { text(value.layoutString, typography = Typography.BODY_MEDIUM, maxLines = 3) },
        onClick = clickable,
    )

    private fun imageResource(bytes: ByteArray, sizePx: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(bytes)
                    .setWidthPx(sizePx)
                    .setHeightPx(sizePx)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED) // JPEG/PNG codificado
                    .build(),
            )
            .build()

    private fun reload(id: String): Clickable = Clickable.Builder()
        .setId(id)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    private fun openApp(context: Context, deviceId: Long?): Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(MainActivity::class.java.name)
        deviceId?.let {
            activity.addKeyToExtraMapping(MainActivity.EXTRA_DEVICE_ID, ActionBuilders.AndroidLongExtra.Builder().setValue(it).build())
        }
        return Clickable.Builder()
            .setId(OPEN_ID)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
            .build()
    }

    private fun openPicker(context: Context, tileId: Int): Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(TilePickerActivity::class.java.name)
            .addKeyToExtraMapping(TilePickerActivity.EXTRA_TILE_ID, ActionBuilders.AndroidIntExtra.Builder().setValue(tileId).build())
            .build()
        return Clickable.Builder()
            .setId(PICK_ID)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build())
            .build()
    }

    private companion object {
        const val MAP_RESOURCE_ID = "map"
        const val REFRESH_ID = "refresh"
        const val ZOOM_IN_ID = "zoom_in"
        const val ZOOM_OUT_ID = "zoom_out"
        const val OPEN_ID = "open"
        const val PICK_ID = "pick"
        const val DEFAULT_ZOOM = 15
        const val RETRY_MILLIS = 15_000L
        // Si los datos tienen menos de esto, no se vuelven a pedir (zoom, reaperturas seguidas)
        const val MIN_REFRESH_MILLIS = 20_000L
        const val REFRESH_TIMEOUT_MILLIS = 4_000L
    }
}
