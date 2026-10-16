package io.github.eduzyrtin.traccarwear.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerRepository
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerStore
import io.github.eduzyrtin.traccarwear.viewer.ui.ViewerViewModel
import io.github.eduzyrtin.traccarwear.viewer.ui.parseTime
import io.github.eduzyrtin.traccarwear.viewer.ui.statusLabel
import io.github.eduzyrtin.traccarwear.viewer.ui.timeAgo
import kotlinx.coroutines.launch

/** Se abre desde el tile para elegir qué dispositivo muestra ese tile. */
class TilePickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.getIntExtra(EXTRA_TILE_ID, -1)
        if (tileId < 0) {
            finish()
            return
        }
        setContent {
            val viewModel: ViewerViewModel = viewModel()
            val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
            val devices = snapshot?.devices.orEmpty().sortedBy { it.name.lowercase() }
            val listState = rememberTransformingLazyColumnState()
            MaterialTheme {
                AppScaffold {
                    ScreenScaffold(scrollState = listState) { contentPadding ->
                        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                            item {
                                ListHeader { Text(stringResource(R.string.tile_choose_device)) }
                            }
                            items(devices, key = { it.id }) { device ->
                                Button(
                                    onClick = {
                                        lifecycleScope.launch {
                                            ViewerStore.setTileDevice(this@TilePickerActivity, tileId, device.id)
                                            ViewerRepository.requestTileUpdate(this@TilePickerActivity)
                                            finish()
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(device.name, maxLines = 1) },
                                    secondaryLabel = {
                                        Text(
                                            "${stringResource(statusLabel(device.status))} · ${timeAgo(parseTime(device.lastUpdate))}",
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TILE_ID = "tile_id"
    }
}
