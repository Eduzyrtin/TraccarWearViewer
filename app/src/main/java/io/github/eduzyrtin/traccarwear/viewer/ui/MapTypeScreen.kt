package io.github.eduzyrtin.traccarwear.viewer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.map.MapType

/**
 * Elección del mapa. Los que necesitan clave solo se pueden elegir si la clave está en las
 * preferencias de Traccar (del usuario o del servidor), con el mismo nombre que usa la web.
 */
@Composable
fun MapTypeScreen(viewModel: ViewerViewModel, onDone: () -> Unit) {
    val mapChoice by viewModel.mapChoice.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val keys = snapshot?.mapKeys.orEmpty()
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.map_type)) }
            }
            items(MapType.entries, key = { it.id }) { type ->
                val available = type.isAvailable(keys)
                val selected = type.id == mapChoice
                Button(
                    onClick = {
                        viewModel.setMapChoice(type.id)
                        onDone()
                    },
                    enabled = available,
                    colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(mapTitle(type.id, snapshot?.map), maxLines = 2) },
                    secondaryLabel = when {
                        !available -> {
                            { Text(stringResource(R.string.map_needs_key, type.keyAttribute.orEmpty()), maxLines = 3) }
                        }
                        type.keyAttribute == null && type != MapType.SERVER -> {
                            { Text(stringResource(R.string.map_no_key)) }
                        }
                        else -> null
                    },
                )
            }
            item {
                Text(
                    stringResource(R.string.map_keys_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Nombre a mostrar; para "el del servidor" incluye cuál es, si se conoce. */
@Composable
fun mapTitle(choice: String, serverMap: String?): String {
    val type = MapType.fromId(choice) ?: MapType.SERVER
    if (type != MapType.SERVER) return type.title
    val serverTitle = MapType.fromId(serverMap)?.takeIf { it != MapType.SERVER }?.title
    return if (serverTitle != null) stringResource(R.string.map_server_named, serverTitle) else stringResource(R.string.map_server)
}
