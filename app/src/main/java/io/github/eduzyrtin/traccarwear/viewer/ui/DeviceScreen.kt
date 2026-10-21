package io.github.eduzyrtin.traccarwear.viewer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.api.Position
import java.util.Locale

@Composable
fun DeviceScreen(viewModel: ViewerViewModel, deviceId: Long, onOpenAlerts: () -> Unit) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val device = snapshot?.devices?.find { it.id == deviceId }
    val position = snapshot?.positionOf(deviceId)
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            if (device == null) {
                item { Text(stringResource(R.string.device_not_found)) }
                return@TransformingLazyColumn
            }
            item {
                ListHeader { Text(device.name, maxLines = 2) }
            }
            item {
                CenteredText("${stringResource(statusLabel(device.status))} · ${timeAgo(parseTime(device.lastUpdate))}")
            }
            position?.let { pos ->
                item {
                    CenteredText(stringResource(R.string.speed, knotsToKmh(pos.speed)))
                }
                item {
                    CenteredText(
                        pos.address ?: String.format(Locale.ROOT, "%.5f, %.5f", pos.latitude, pos.longitude),
                        secondary = true,
                    )
                }
                item {
                    Button(
                        onClick = { openMap(context, device.name, pos) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.open_map)) },
                    )
                }
            }
            item {
                Button(
                    onClick = onOpenAlerts,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notifications)) },
                )
            }
            item {
                val pinned = deviceId in favorites
                Button(
                    onClick = { viewModel.toggleFavorite(deviceId) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (pinned) R.string.remove_from_tile else R.string.show_in_tile)) },
                )
            }
        }
    }
}

@Composable
private fun CenteredText(text: String, secondary: Boolean = false) {
    Text(
        text,
        style = if (secondary) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// Mapa en el reloj si hay alguna app que lo abra; si no, en el teléfono
private fun openMap(context: Context, name: String, position: Position) {
    val coordinates = "${position.latitude},${position.longitude}"
    val geo = Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=$coordinates(${Uri.encode(name)})".toUri())
    if (geo.resolveActivity(context.packageManager) != null) {
        context.startActivity(geo)
        return
    }
    val web = Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/search/?api=1&query=$coordinates".toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
    RemoteActivityHelper(context).startRemoteActivity(web)
    Toast.makeText(context, R.string.opened_on_phone, Toast.LENGTH_SHORT).show()
}
