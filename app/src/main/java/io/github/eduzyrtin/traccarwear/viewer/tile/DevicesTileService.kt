package io.github.eduzyrtin.traccarwear.viewer.tile

import android.content.Context
import android.text.format.DateUtils
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import io.github.eduzyrtin.traccarwear.viewer.MainActivity
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.api.Device
import io.github.eduzyrtin.traccarwear.viewer.data.Snapshot
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerRepository
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerStore
import io.github.eduzyrtin.traccarwear.viewer.ui.parseTime
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/** Tile con los dispositivos fijados (o los últimos activos): nombre, estado por color y última actualización. */
class DevicesTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val context: Context = this@DevicesTileService
        val account = ViewerStore.account(context).first()
        var snapshot = ViewerStore.snapshot(context).first()
        if (account != null) {
            val stale = snapshot == null || System.currentTimeMillis() - snapshot.updatedAt > STALE_MILLIS
            if (stale || requestParams.currentState.lastClickableId == REFRESH_ID) {
                snapshot = try {
                    withTimeout(REFRESH_TIMEOUT_MILLIS) { ViewerRepository.refresh(context, updateTile = false) }
                } catch (e: Exception) {
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    snapshot
                }
            }
        }
        val current = snapshot
        val favorites = ViewerStore.favorites(context).first()

        val layout = primaryLayout(
            titleSlot = { text(context.getString(R.string.tile_title).layoutString) },
            mainSlot = {
                when {
                    account == null -> message(context.getString(R.string.tile_sign_in))
                    current == null || current.devices.isEmpty() -> message(context.getString(R.string.no_devices))
                    else -> deviceList(pickDevices(current, favorites))
                }
            },
            bottomSlot = if (account == null) null else {
                {
                    textEdgeButton(
                        onClick = refreshClickable(),
                        labelContent = { text(context.getString(R.string.refresh).layoutString) },
                    )
                }
            },
            onClick = openAppClickable(context),
        )

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    private fun pickDevices(snapshot: Snapshot, favorites: Set<Long>): List<Device> {
        val pinned = snapshot.devices.filter { it.id in favorites }.sortedBy { it.name.lowercase() }
        val devices = pinned.ifEmpty { snapshot.devices.sortedByDescending { parseTime(it.lastUpdate) ?: 0L } }
        return devices.take(MAX_DEVICES)
    }

    private fun MaterialScope.deviceList(devices: List<Device>): LayoutElement {
        val now = System.currentTimeMillis()
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        devices.forEach { device ->
            val color = when (device.status) {
                "online" -> colorScheme.primary
                "offline" -> colorScheme.error
                else -> colorScheme.onSurfaceVariant
            }
            val ago = parseTime(device.lastUpdate)?.let {
                DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
            } ?: "—"
            column.addContent(
                text("${device.name} · $ago".layoutString, typography = Typography.BODY_MEDIUM, color = color, maxLines = 1),
            )
        }
        return column.build()
    }

    private fun MaterialScope.message(value: String): LayoutElement =
        text(value.layoutString, typography = Typography.BODY_MEDIUM, maxLines = 3)

    private fun refreshClickable(): Clickable = Clickable.Builder()
        .setId(REFRESH_ID)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    private fun openAppClickable(context: Context): Clickable = Clickable.Builder()
        .setId(OPEN_ID)
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build(),
                )
                .build(),
        )
        .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val REFRESH_ID = "refresh"
        const val OPEN_ID = "open"
        const val MAX_DEVICES = 3
        const val STALE_MILLIS = 5 * 60_000L
        const val FRESHNESS_MILLIS = 15 * 60_000L
        const val REFRESH_TIMEOUT_MILLIS = 8_000L
    }
}
