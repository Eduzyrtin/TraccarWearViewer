package io.github.eduzyrtin.traccarwear.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R

/**
 * Campo de texto para el reloj. Al tocarlo aparece el teclado del sistema
 * (y en muchos relojes, la opción de escribir desde el teléfono).
 */
@Composable
fun TextInputScreen(
    title: String,
    initialValue: String,
    hint: String,
    keyboardType: KeyboardType,
    onSave: (String) -> Unit,
) {
    val state = rememberTextFieldState(initialValue)
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(title) }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.text.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        state = state,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                        onKeyboardAction = { onSave(state.text.toString()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Button(
                    onClick = { onSave(state.text.toString()) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.save)) },
                )
            }
        }
    }
}
