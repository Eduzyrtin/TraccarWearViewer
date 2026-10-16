package io.github.eduzyrtin.traccarwear.viewer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.api.Device

@Composable
fun DevicesScreen(viewModel: ViewerViewModel, onOpenDevice: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val devices = snapshot?.devices.orEmpty()
        .sortedWith(compareByDescending<Device> { it.id in favorites }.thenBy { it.name.lowercase() })
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = viewModel::refresh, enabled = !busy) {
                Text(stringResource(R.string.refresh))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.devices)) }
            }
            error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when {
                snapshot == null -> item { CircularProgressIndicator() }
                devices.isEmpty() -> item {
                    Text(stringResource(R.string.no_devices), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            items(devices, key = { it.id }) { device ->
                Button(
                    onClick = { onOpenDevice(device.id) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text((if (device.id in favorites) "★ " else "") + device.name, maxLines = 1) },
                    secondaryLabel = {
                        Text("${stringResource(statusLabel(device.status))} · ${timeAgo(parseTime(device.lastUpdate))}", maxLines = 1)
                    },
                )
            }
            item {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings)) },
                )
            }
        }
    }
}

