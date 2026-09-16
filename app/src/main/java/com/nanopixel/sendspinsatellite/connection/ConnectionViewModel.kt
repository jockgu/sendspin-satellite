package com.nanopixel.sendspinsatellite.connection

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanopixel.sendspinsatellite.playback.PlaybackService
import com.nanopixel.sendspinsatellite.playback.toUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val preferences = ConnectionPreferences(app)
    private val defaultPlayerName = PlayerNamePolicy.defaultFor(Build.MODEL)
    private var automaticConnectionAttempted = false
    private var startupAttempted = false
    private val initialServer = preferences.savedServer()
    private val _uiState = MutableStateFlow(
        ConnectionUiState(
            savedServer = initialServer,
            serverAddress = initialServer?.address.orEmpty(),
            playerName = preferences.playerName() ?: defaultPlayerName,
        ),
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    init {
        viewModelScope.launch {
            PlaybackService.state.collect { status ->
                if (status.connectionState in setOf(
                        ConnectionState.READY,
                        ConnectionState.BUFFERING,
                        ConnectionState.PLAYING,
                    )
                ) {
                    automaticConnectionAttempted = false
                }
                if (automaticConnectionAttempted && status.connectionState == ConnectionState.ERROR) {
                    _uiState.value = status.toUiState(_uiState.value).copy(
                        detail = "Couldn't connect to your preferred server.",
                    )
                    automaticConnectionAttempted = false
                    return@collect
                }
                val preferredServer = if (status.connectionState in setOf(
                        ConnectionState.READY,
                        ConnectionState.BUFFERING,
                        ConnectionState.PLAYING,
                    )
                ) {
                    preferences.savedServer()
                } else {
                    _uiState.value.savedServer
                }
                _uiState.value = status.toUiState(_uiState.value).copy(
                    savedServer = preferredServer,
                    serverAddress = preferredServer?.address ?: _uiState.value.serverAddress,
                )
            }
        }
    }

    fun start() {
        if (startupAttempted) return
        startupAttempted = true
        if (preferences.savedServer() == null &&
            PlaybackService.state.value.connectionState == ConnectionState.DISCONNECTED
        ) {
            discover()
        } else {
            autoConnect()
        }
    }

    fun autoConnect() {
        val server = preferences.savedServer()
        val activeState = PlaybackService.state.value.connectionState
        if (!shouldAutoConnect(server?.address, automaticConnectionAttempted, activeState)) return
        automaticConnectionAttempted = true
        _uiState.value = _uiState.value.copy(
            savedServer = server,
            serverAddress = server?.address.orEmpty(),
            connectionState = ConnectionState.CONNECTING,
            detail = "Connecting to your Sendspin system.",
        )
        connect()
    }

    fun updateServerAddress(address: String) {
        _uiState.value = _uiState.value.copy(serverAddress = address)
    }

    fun updatePlayerName(name: String) {
        val validation = PlayerNamePolicy.validate(name)
        validation.normalized?.let { normalizedName ->
            preferences.savePlayerName(normalizedName)
        }
        _uiState.value = _uiState.value.copy(
            playerName = name,
            playerNameError = validation.error,
        )
    }

    fun connect() {
        val currentState = _uiState.value
        val address = normalizeServerAddress(currentState.serverAddress)
        val playerName = PlayerNamePolicy.validate(currentState.playerName)
        if (!playerName.isValid) {
            _uiState.value = currentState.copy(playerNameError = playerName.error)
            return
        }
        if (address == null) {
            _uiState.value = currentState.copy(
                connectionState = ConnectionState.ERROR,
                detail = "Enter a server IP address, hostname, or full ws:// address.",
            )
            return
        }
        val normalizedPlayerName = playerName.normalized.orEmpty()
        preferences.savePlayerName(normalizedPlayerName)
        _uiState.value = currentState.copy(
            serverAddress = address,
            playerName = normalizedPlayerName,
            playerNameError = null,
            connectionState = ConnectionState.CONNECTING,
            detail = "Connecting to your Sendspin system.",
        )
        val serverName = currentState.savedServer
            ?.takeIf { it.address == address }
            ?.name
        PlaybackService.connect(app, address, normalizedPlayerName, serverName)
    }

    fun discover() {
        val currentState = _uiState.value
        val playerName = PlayerNamePolicy.validate(currentState.playerName)
        if (!playerName.isValid) {
            _uiState.value = currentState.copy(playerNameError = playerName.error)
            return
        }
        val normalizedPlayerName = playerName.normalized.orEmpty()
        preferences.savePlayerName(normalizedPlayerName)
        _uiState.value = currentState.copy(
            playerName = normalizedPlayerName,
            playerNameError = null,
            discoveredServers = emptyList(),
            connectionState = ConnectionState.DISCOVERING,
            detail = "Finding your Sendspin system.",
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
            detail = "Allow local network access so Sendspin Satellite can find your server.",
        )
    }

    fun disconnect() {
        PlaybackService.stop(app)
    }

    fun forgetServer() {
        preferences.forgetServer()
        automaticConnectionAttempted = false
        PlaybackService.stop(app)
        _uiState.value = _uiState.value.copy(
            savedServer = null,
            serverAddress = "",
            serverName = null,
            discoveredServers = emptyList(),
            connectionState = ConnectionState.DISCONNECTED,
            detail = "Let's find your Sendspin system.",
        )
    }
}

internal fun normalizeServerAddress(input: String): String? {
    val value = input.trim()
    if (value.isBlank() || value.any(Char::isWhitespace)) return null
    val address = when {
        value.startsWith("ws://", ignoreCase = true) -> "ws://${value.substringAfter("://")}"
        value.contains("://") || value.contains('/') || value.contains('?') || value.contains('#') -> return null
        value.count { it == ':' } > 1 -> "ws://[$value]:8927/sendspin"
        value.startsWith('[') || value.contains(':') -> return null
        else -> "ws://$value:8927/sendspin"
    }
    return runCatching {
        val uri = URI(address)
        address.takeIf { uri.scheme.equals("ws", ignoreCase = true) && !uri.host.isNullOrBlank() }
    }.getOrNull()
}
