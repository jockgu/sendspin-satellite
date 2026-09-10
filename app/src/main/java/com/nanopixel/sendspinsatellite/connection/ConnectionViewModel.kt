package com.nanopixel.sendspinsatellite.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanopixel.sendspinsatellite.playback.PlaybackService
import com.nanopixel.sendspinsatellite.playback.toUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val preferences = app.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        ConnectionUiState(serverAddress = preferences.getString(SERVER_ADDRESS_KEY, "").orEmpty()),
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    init {
        viewModelScope.launch {
            PlaybackService.state.collect { status ->
                _uiState.value = status.toUiState(_uiState.value.serverAddress)
            }
        }
    }

    fun updateServerAddress(address: String) {
        _uiState.value = _uiState.value.copy(serverAddress = address)
    }

    fun connect() {
        val address = _uiState.value.serverAddress.trim()
        if (!address.startsWith("ws://")) {
            _uiState.value = _uiState.value.copy(
                connectionState = ConnectionState.ERROR,
                detail = "Sendspin uses a ws:// server address, for example ws://server.local:8927/sendspin.",
            )
            return
        }
        preferences.edit().putString(SERVER_ADDRESS_KEY, _uiState.value.serverAddress.trim()).apply()
        _uiState.value = _uiState.value.copy(serverAddress = address)
        PlaybackService.connect(app, address)
    }

    fun disconnect() {
        PlaybackService.stop(app)
    }

    private companion object {
        const val PREFERENCES_NAME = "sendspin_settings"
        const val SERVER_ADDRESS_KEY = "server_address"
    }
}
