package com.nanopixel.sendspinsatellite.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nanopixel.sendspinsatellite.protocol.SendspinSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        ConnectionUiState(serverAddress = preferences.getString(SERVER_ADDRESS_KEY, "").orEmpty()),
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()
    private val session = SendspinSession(application, object : SendspinSession.Listener {
        override fun onState(state: SendspinSession.SessionState) {
            _uiState.value = _uiState.value.copy(
                connectionState = state.toUiState(),
                detail = state.detail(),
            )
        }

        override fun onDiagnostics(diagnostics: SendspinSession.Diagnostics) {
            _uiState.value = _uiState.value.copy(
                serverName = diagnostics.serverName ?: _uiState.value.serverName,
                roundTripUs = diagnostics.roundTripUs.takeIf { it > 0 },
                clockOffsetUs = diagnostics.offsetUs.takeIf { diagnostics.samples > 0 },
                clockSamples = diagnostics.samples,
                detail = diagnostics.message ?: _uiState.value.detail,
            )
        }
    })

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
        _uiState.value = _uiState.value.copy(serverAddress = address, serverName = null, roundTripUs = null, clockOffsetUs = null, clockSamples = 0)
        session.connect(address)
    }

    fun disconnect() {
        session.close()
        _uiState.value = _uiState.value.copy(
            connectionState = ConnectionState.DISCONNECTED,
            detail = "Disconnected from Sendspin server.",
        )
    }

    override fun onCleared() {
        session.shutdown()
        super.onCleared()
    }

    private companion object {
        const val PREFERENCES_NAME = "sendspin_settings"
        const val SERVER_ADDRESS_KEY = "server_address"
    }
}

private fun SendspinSession.SessionState.toUiState(): ConnectionState = when (this) {
    SendspinSession.SessionState.CONNECTING -> ConnectionState.CONNECTING
    SendspinSession.SessionState.HANDSHAKING -> ConnectionState.HANDSHAKING
    SendspinSession.SessionState.SYNCHRONISING -> ConnectionState.SYNCHRONISING
    SendspinSession.SessionState.SYNCHRONISED -> ConnectionState.READY
    SendspinSession.SessionState.DISCONNECTED -> ConnectionState.DISCONNECTED
    SendspinSession.SessionState.ERROR -> ConnectionState.ERROR
}

private fun SendspinSession.SessionState.detail(): String = when (this) {
    SendspinSession.SessionState.CONNECTING -> "Opening a Sendspin connection."
    SendspinSession.SessionState.HANDSHAKING -> "Establishing the encrypted Sendspin session."
    SendspinSession.SessionState.SYNCHRONISING -> "Measuring the server clock."
    SendspinSession.SessionState.SYNCHRONISED -> "Clock synchronised. Native PCM playback is ready."
    SendspinSession.SessionState.DISCONNECTED -> "Disconnected from Sendspin server."
    SendspinSession.SessionState.ERROR -> "The Sendspin connection failed."
}
