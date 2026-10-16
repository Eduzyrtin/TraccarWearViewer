package io.github.eduzyrtin.traccarwear.viewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.github.eduzyrtin.traccarwear.viewer.data.Account
import io.github.eduzyrtin.traccarwear.viewer.ui.AccountState
import io.github.eduzyrtin.traccarwear.viewer.ui.DeviceScreen
import io.github.eduzyrtin.traccarwear.viewer.ui.DevicesScreen
import io.github.eduzyrtin.traccarwear.viewer.ui.LoginField
import io.github.eduzyrtin.traccarwear.viewer.ui.LoginScreen
import io.github.eduzyrtin.traccarwear.viewer.ui.MapScreen
import io.github.eduzyrtin.traccarwear.viewer.ui.MapTypeScreen
import io.github.eduzyrtin.traccarwear.viewer.ui.SettingsScreen
import io.github.eduzyrtin.traccarwear.viewer.ui.TextInputScreen
import io.github.eduzyrtin.traccarwear.viewer.ui.ViewerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectFrom(intent)
        setContent { ViewerApp(viewModel) }
    }

    // Abierta desde el tile: centrar el mapa en ese dispositivo
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        selectFrom(intent)
    }

    private fun selectFrom(intent: Intent) {
        val deviceId = intent.getLongExtra(EXTRA_DEVICE_ID, -1L)
        if (deviceId >= 0) viewModel.select(deviceId)
    }

    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
    }
}

@Composable
private fun ViewerApp(viewModel: ViewerViewModel) {
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    MaterialTheme {
        AppScaffold {
            when (val state = accountState) {
                AccountState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                AccountState.LoggedOut -> LoginNavigation(viewModel)
                is AccountState.LoggedIn -> MainNavigation(viewModel, state.account)
            }
        }
    }
}

@Composable
private fun LoginNavigation(viewModel: ViewerViewModel) {
    val navController = rememberSwipeDismissableNavController()
    val form by viewModel.loginForm.collectAsStateWithLifecycle()
    SwipeDismissableNavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(viewModel, onEdit = { field -> navController.navigate("edit_${field.name}") })
        }
        LoginField.entries.forEach { field ->
            composable("edit_${field.name}") {
                TextInputScreen(
                    title = stringResource(field.title),
                    initialValue = form.get(field),
                    hint = stringResource(field.hint),
                    keyboardType = when (field) {
                        LoginField.SERVER -> KeyboardType.Uri
                        LoginField.EMAIL -> KeyboardType.Email
                        LoginField.PASSWORD -> KeyboardType.Ascii
                        LoginField.TOKEN -> KeyboardType.Ascii
                    },
                    onSave = { value ->
                        viewModel.setLoginField(field, value)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

@Composable
private fun MainNavigation(viewModel: ViewerViewModel, account: Account) {
    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = "map") {
        composable("map") {
            MapScreen(
                viewModel,
                onOpenList = { navController.navigate("devices") },
                onOpenDevice = { id -> navController.navigate("device/$id") },
            )
        }
        // Elegir en la lista vuelve al mapa centrado en ese dispositivo
        composable("devices") {
            DevicesScreen(
                viewModel,
                onOpenDevice = { id ->
                    viewModel.select(id)
                    navController.popBackStack("map", inclusive = false)
                },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("device/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            DeviceScreen(viewModel, id)
        }
        composable("settings") {
            SettingsScreen(viewModel, account, onOpenMaps = { navController.navigate("maps") })
        }
        composable("maps") {
            MapTypeScreen(viewModel, onDone = { navController.popBackStack() })
        }
    }
}

private val LoginField.title: Int
    get() = when (this) {
        LoginField.SERVER -> R.string.server_url
        LoginField.EMAIL -> R.string.email
        LoginField.PASSWORD -> R.string.password
        LoginField.TOKEN -> R.string.token
    }

private val LoginField.hint: Int
    get() = when (this) {
        LoginField.SERVER -> R.string.server_url_hint
        LoginField.EMAIL -> R.string.email_hint
        LoginField.PASSWORD -> R.string.password
        LoginField.TOKEN -> R.string.token_hint
    }
