package io.github.eduzyrtin.traccarwear.viewer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.data.IntervalUnit
import io.github.eduzyrtin.traccarwear.viewer.data.RefreshInterval

/** Cada cuánto se refresca el tile y se buscan avisos: un número libre y una unidad (minutos, horas o días). */
@Composable
fun RefreshIntervalScreen(viewModel: ViewerViewModel, onEditValue: () -> Unit) {
    val interval by viewModel.refreshInterval.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.refresh_interval)) }
            }
            item {
                Button(
                    onClick = onEditValue,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.refresh_every)) },
                    secondaryLabel = { Text(interval.value.toString()) },
                )
            }
            item {
                // Pulsar pasa a la siguiente unidad: minutos → horas → días → minutos
                Button(
                    onClick = {
                        val next = IntervalUnit.entries[(interval.unit.ordinal + 1) % IntervalUnit.entries.size]
                        viewModel.setRefreshInterval(interval.withUnit(next))
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.refresh_unit)) },
                    secondaryLabel = { Text(stringResource(interval.unit.nameRes())) },
                )
            }
            item {
                Text(
                    stringResource(R.string.refresh_interval_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** "15 min", "2 h", "1 d"... */
@Composable
fun RefreshInterval.label(): String = stringResource(unit.shortRes(), value)

@StringRes
private fun IntervalUnit.nameRes(): Int = when (this) {
    IntervalUnit.MINUTES -> R.string.unit_minutes
    IntervalUnit.HOURS -> R.string.unit_hours
    IntervalUnit.DAYS -> R.string.unit_days
}

@StringRes
private fun IntervalUnit.shortRes(): Int = when (this) {
    IntervalUnit.MINUTES -> R.string.interval_minutes
    IntervalUnit.HOURS -> R.string.interval_hours
    IntervalUnit.DAYS -> R.string.interval_days
}
