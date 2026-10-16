package io.github.eduzyrtin.traccarwear.viewer.ui

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
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R

@Composable
fun LoginScreen(viewModel: ViewerViewModel, onEdit: (LoginField) -> Unit) {
    val form by viewModel.loginForm.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notSet = stringResource(R.string.not_set)
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.sign_in)) }
            }
            item {
                FieldButton(stringResource(R.string.server_url), form.server.ifBlank { notSet }) { onEdit(LoginField.SERVER) }
            }
            item {
                FieldButton(stringResource(R.string.email), form.email.ifBlank { notSet }) { onEdit(LoginField.EMAIL) }
            }
            item {
                FieldButton(
                    stringResource(R.string.password),
                    form.password.ifEmpty { notSet },
                ) { onEdit(LoginField.PASSWORD) }
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
            item {
                if (busy) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = viewModel::login,
                        enabled = form.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sign_in)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldButton(label: String, value: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(),
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        secondaryLabel = { Text(value, maxLines = 3) },
    )
}
