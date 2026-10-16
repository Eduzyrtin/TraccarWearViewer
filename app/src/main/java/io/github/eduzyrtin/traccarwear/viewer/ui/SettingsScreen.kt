package io.github.eduzyrtin.traccarwear.viewer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.data.Account
import io.github.eduzyrtin.traccarwear.viewer.data.NotificationMode

@Composable
fun SettingsScreen(viewModel: ViewerViewModel, account: Account, onOpenMaps: () -> Unit) {
    val context = LocalContext.current
    val notificationMode by viewModel.notificationMode.collectAsStateWithLifecycle()
    val mapChoice by viewModel.mapChoice.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val autoHide by viewModel.autoHideMapControls.collectAsStateWithLifecycle()

    // Al activar los avisos por primera vez hace falta el permiso de notificaciones
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.setNotificationMode(NotificationMode.AWAY_FROM_PHONE) }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.settings)) }
            }
            item {
                Button(
                    onClick = onOpenMaps,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.map_type)) },
                    secondaryLabel = { Text(mapTitle(mapChoice, snapshot?.map), maxLines = 2) },
                )
            }
            item {
                SwitchButton(
                    checked = autoHide,
                    onCheckedChange = viewModel::setAutoHideMapControls,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.map_buttons_autohide)) },
                    secondaryLabel = { Text(stringResource(R.string.map_buttons_autohide_summary)) },
                )
            }
            item {
                // Pulsar pasa al siguiente modo: no → lejos del móvil → siempre → no
                Button(
                    onClick = {
                        val next = NotificationMode.entries[(notificationMode.ordinal + 1) % NotificationMode.entries.size]
                        val needsPermission = notificationMode == NotificationMode.OFF &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationMode(next)
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notifications)) },
                    secondaryLabel = { Text(stringResource(notificationMode.label()), maxLines = 3) },
                )
            }
            item {
                Button(
                    onClick = viewModel::logout,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sign_out)) },
                    secondaryLabel = {
                        Text("${account.userName} · ${account.serverUrl.toUri().host.orEmpty()}", maxLines = 2)
                    },
                )
            }
        }
    }
}

@StringRes
private fun NotificationMode.label(): Int = when (this) {
    NotificationMode.OFF -> R.string.notifications_off
    NotificationMode.AWAY_FROM_PHONE -> R.string.notifications_away
    NotificationMode.ALWAYS -> R.string.notifications_always
}
