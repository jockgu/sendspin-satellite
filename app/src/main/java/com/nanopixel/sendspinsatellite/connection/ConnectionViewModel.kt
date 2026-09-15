package com.nanopixel.sendspinsatellite.connection

import android.app.Application
import android.os.Build
import androidx.core.content.edit
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
    private val defaultPlayerName = PlayerNamePolicy.defaultFor(Build.MODEL)
    private var automaticConnectionAttempted = false
    private val _uiState = MutableStateFlow(
        ConnectionUiState(
            serverAddress = preferences.getString(LAST_WORKING_SERVER_KEY, "").orEmpty(),
            playerName = preferences.getString(PLAYER_NAME_KEY, null) ?: defaultPlayerName,
        ),
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    init {
        viewModelScope.launch {
            PlaybackService.state.collect { status ->
                if (status.connectionState == ConnectionState.READY) {
                    val address = _uiState.value.serverAddress.trim()
                    if (address.startsWith("ws://")) {
                        preferences.edit { putString(LAST_WORKING_SERVER_KEY, address) }
                    }
                    automaticConnectionAttempted = false
                }
                if (automaticConnectionAttempted && status.connectionState == ConnectionState.ERROR) {
                    _uiState.value = status.toUiState(_uiState.value).copy(
                        detail = "The last known server could not be reached. Connect manually or find a local server.",
                    )
                    automaticConnectionAttempted = false
                    return@collect
                }
                _uiState.value = status.toUiState(_uiState.value)
            }
        }
    }

    fun hasLastWorkingServer(): Boolean =
        preferences.getString(LAST_WORKING_SERVER_KEY, null)?.isNotBlank() == true

    fun autoConnect() {
        val address = preferences.getString(LAST_WORKING_SERVER_KEY, null)
        if (!shouldAutoConnect(address, automaticConnectionAttempted, _uiState.value.connectionState)) return
        automaticConnectionAttempted = true
        _uiState.value = _uiState.value.copy(
            serverAddress = address.orEmpty(),
            detail = "Connecting to the last known Sendspin server.",
        )
        connect()
    }

    fun updateServerAddress(address: String) {
        _uiState.value = _uiState.value.copy(serverAddress = address)
    }

    fun updatePlayerName(name: String) {
        val validation = PlayerNamePolicy.validate(name)
        validation.normalized?.let { normalizedName ->
            preferences.edit { putString(PLAYER_NAME_KEY, normalizedName) }
        }
        _uiState.value = _uiState.value.copy(
            playerName = name,
            playerNameError = validation.error,
        )
    }

    fun connect() {
        val currentState = _uiState.value
        val address = currentState.serverAddress.trim()
        val playerName = PlayerNamePolicy.validate(currentState.playerName)
        if (!playerName.isValid) {
            _uiState.value = currentState.copy(playerNameError = playerName.error)
            return
        }
        if (!address.startsWith("ws://")) {
            _uiState.value = currentState.copy(
                connectionState = ConnectionState.ERROR,
                detail = "Sendspin uses a ws:// server address, for example ws://server.local:8927/sendspin.",
            )
            return
        }
        val normalizedPlayerName = playerName.normalized.orEmpty()
        preferences.edit {
            putString(PLAYER_NAME_KEY, normalizedPlayerName)
        }
        _uiState.value = currentState.copy(
            serverAddress = address,
            playerName = normalizedPlayerName,
            playerNameError = null,
        )
        PlaybackService.connect(app, address, normalizedPlayerName)
    }

    fun discover() {
        val currentState = _uiState.value
        val playerName = PlayerNamePolicy.validate(currentState.playerName)
        if (!playerName.isValid) {
            _uiState.value = currentState.copy(playerNameError = playerName.error)
            return
        }
        val normalizedPlayerName = playerName.normalized.orEmpty()
        preferences.edit { putString(PLAYER_NAME_KEY, normalizedPlayerName) }
        _uiState.value = currentState.copy(
            playerName = normalizedPlayerName,
            playerNameError = null,
            discoveredServers = emptyList(),
        )
        PlaybackService.discover(app, normalizedPlayerName)
    }

    fun selectDiscoveredServer(id: String) {
        if (id.isBlank()) return
        PlaybackService.selectDiscoveredServer(app, id)
    }

    fun localNetworkPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            connectionState = ConnectionState.ERROR,
            detail = "Allow local network access to find or connect to a Home Assistant server.",
        )
    }

    fun disconnect() {
        PlaybackService.stop(app)
    }

    private companion object {
        const val PREFERENCES_NAME = "sendspin_settings"
        private const val LAST_WORKING_SERVER_KEY = "last_working_server"
        const val PLAYER_NAME_KEY = "player_name"
    }
}
