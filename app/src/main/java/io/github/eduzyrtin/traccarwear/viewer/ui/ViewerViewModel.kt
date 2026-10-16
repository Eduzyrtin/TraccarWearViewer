package io.github.eduzyrtin.traccarwear.viewer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eduzyrtin.traccarwear.viewer.BuildConfig
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.api.HttpException
import io.github.eduzyrtin.traccarwear.viewer.api.UnauthorizedException
import io.github.eduzyrtin.traccarwear.viewer.data.Account
import io.github.eduzyrtin.traccarwear.viewer.data.NotificationMode
import io.github.eduzyrtin.traccarwear.viewer.data.Snapshot
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerRepository
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerStore
import io.github.eduzyrtin.traccarwear.viewer.map.MapType
import io.github.eduzyrtin.traccarwear.viewer.notify.EventsWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

sealed interface AccountState {
    data object Loading : AccountState
    data object LoggedOut : AccountState
    data class LoggedIn(val account: Account) : AccountState
}

enum class LoginField { SERVER, EMAIL, PASSWORD, TOKEN }

data class LoginForm(
    val server: String = "",
    val email: String = "",
    val password: String = "",
    // Alternativa a correo y contraseña (necesaria con SSO/OpenID)
    val token: String = "",
) {
    val canSubmit: Boolean
        get() = server.isNotBlank() && (token.isNotBlank() || (email.isNotBlank() && password.isNotEmpty()))

    fun get(field: LoginField): String = when (field) {
        LoginField.SERVER -> server
        LoginField.EMAIL -> email
        LoginField.PASSWORD -> password
        LoginField.TOKEN -> token
    }

    fun with(field: LoginField, value: String): LoginForm = when (field) {
        LoginField.SERVER -> copy(server = value.trim())
        LoginField.EMAIL -> copy(email = value.trim())
        LoginField.PASSWORD -> copy(password = value)
        LoginField.TOKEN -> copy(token = value.trim())
    }
}

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    val accountState: StateFlow<AccountState> = ViewerStore.account(application)
        .map { if (it == null) AccountState.LoggedOut else AccountState.LoggedIn(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccountState.Loading)

    val snapshot: StateFlow<Snapshot?> = ViewerStore.snapshot(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val favorites: StateFlow<Set<Long>> = ViewerStore.favorites(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val notificationMode: StateFlow<NotificationMode> = ViewerStore.notificationMode(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationMode.OFF)

    val autoHideMapControls: StateFlow<Boolean> = ViewerStore.autoHideMapControls(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val mapChoice: StateFlow<String> = ViewerStore.mapChoice(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapType.SERVER.id)

    private val _loginForm = MutableStateFlow(testLoginForm())
    val loginForm: StateFlow<LoginForm> = _loginForm.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setLoginField(field: LoginField, value: String) {
        _loginForm.update { it.with(field, value) }
    }

    fun login() = launchBusy {
        val form = _loginForm.value
        if (form.token.isNotBlank()) {
            ViewerRepository.loginWithToken(app, form.server, form.token)
        } else {
            ViewerRepository.login(app, form.server, form.email, form.password)
        }
        _loginForm.value = LoginForm()
    }

    fun refresh() = launchBusy { ViewerRepository.refresh(app) }

    // Dispositivo destacado en el mapa (tocado en el mapa, elegido en la lista o abierto desde el tile)
    private val _selectedDeviceId = MutableStateFlow<Long?>(null)
    val selectedDeviceId: StateFlow<Long?> = _selectedDeviceId.asStateFlow()

    fun select(deviceId: Long?) {
        _selectedDeviceId.value = deviceId
    }

    fun toggleFavorite(deviceId: Long) {
        viewModelScope.launch {
            ViewerStore.toggleFavorite(app, deviceId)
            ViewerRepository.requestTileUpdate(app)
        }
    }

    fun setNotificationMode(mode: NotificationMode) {
        viewModelScope.launch {
            ViewerStore.setNotificationMode(app, mode)
            if (mode == NotificationMode.OFF) EventsWorker.cancel(app) else EventsWorker.schedule(app)
        }
    }

    fun setAutoHideMapControls(enabled: Boolean) {
        viewModelScope.launch { ViewerStore.setAutoHideMapControls(app, enabled) }
    }

    fun setMapChoice(id: String) {
        viewModelScope.launch {
            ViewerStore.setMapChoice(app, id)
            ViewerRepository.requestTileUpdate(app)
        }
    }

    fun logout() {
        viewModelScope.launch { ViewerRepository.logout(app) }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                block()
            } catch (_: UnauthorizedException) {
                _error.value = app.getString(R.string.error_unauthorized)
                // Token revocado o caducado: volver a la pantalla de acceso
                if (accountState.value is AccountState.LoggedIn) ViewerStore.clear(app)
            } catch (e: HttpException) {
                _error.value = app.getString(R.string.error_http, e.code)
            } catch (_: IOException) {
                _error.value = app.getString(R.string.error_network)
            } catch (_: SerializationException) {
                _error.value = app.getString(R.string.error_response)
            } catch (_: IllegalArgumentException) {
                _error.value = app.getString(R.string.error_url)
            } finally {
                _busy.value = false
            }
        }
    }
}

// Solo en debug: valores de test-defaults.properties (no versionado). En release salen vacíos.
private fun testLoginForm() = LoginForm(
    server = BuildConfig.TEST_SERVER,
    email = BuildConfig.TEST_EMAIL,
    password = BuildConfig.TEST_PASSWORD,
)
