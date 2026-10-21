package io.github.eduzyrtin.traccarwear.viewer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.api.Device
import io.github.eduzyrtin.traccarwear.viewer.notify.EventType

/** Lista de dispositivos con un resumen de qué avisa cada uno. */
@Composable
fun AlertDevicesScreen(viewModel: ViewerViewModel, onOpenDevice: (Long) -> Unit) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val filter by viewModel.alertFilter.collectAsStateWithLifecycle()
    val devices = snapshot?.devices.orEmpty().sortedBy { it.name.lowercase() }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.alerts_by_device)) }
            }
            when {
                snapshot == null -> item { CircularProgressIndicator() }
                devices.isEmpty() -> item {
                    Text(stringResource(R.string.no_devices), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            items(devices, key = { it.id }) { device: Device ->
                val summary = when {
                    filter.isDeviceMuted(device.id) -> R.string.alerts_off
                    filter.disabledTypes(device.id).isNotEmpty() -> R.string.alerts_some_off
                    else -> R.string.alerts_all_events
                }
                Button(
                    onClick = { onOpenDevice(device.id) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(device.name, maxLines = 1) },
                    secondaryLabel = { Text(stringResource(summary), maxLines = 1) },
                )
            }
        }
    }
}

/**
 * Avisos de un dispositivo: un interruptor general y uno por tipo de evento de Traccar. Los tipos
 * son los de las notificaciones que el usuario tiene en el servidor.
 */
@Composable
fun DeviceAlertsScreen(viewModel: ViewerViewModel, deviceId: Long) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val filter by viewModel.alertFilter.collectAsStateWithLifecycle()
    val ruleTypes by viewModel.ruleTypes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadRuleTypes() }

    val device = snapshot?.devices?.find { it.id == deviceId }
    val muted = filter.isDeviceMuted(deviceId)
    val types = EventType.available(ruleTypes)
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(device?.name ?: stringResource(R.string.device_not_found), maxLines = 2) }
            }
            item {
                SwitchButton(
                    checked = !muted,
                    onCheckedChange = { viewModel.setDeviceAlertsMuted(deviceId, !it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.alerts_for_device)) },
                )
            }
            if (types.isEmpty()) {
                item {
                    HintText(stringResource(R.string.alerts_no_rules))
                }
            }
            items(types, key = { it.id }) { type ->
                SwitchButton(
                    checked = type.id !in filter.disabledTypes(deviceId),
                    onCheckedChange = { viewModel.setDeviceAlertType(deviceId, type.id, it) },
                    enabled = !muted,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(type.label), maxLines = 2) },
                )
            }
            item {
                HintText(stringResource(R.string.alerts_rules_hint))
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
