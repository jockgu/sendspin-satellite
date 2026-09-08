package com.jockgu.sendspinsatellite.connection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        ConnectionUiState(serverAddress = preferences.getString(SERVER_ADDRESS_KEY, "").orEmpty()),
    )
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    fun updateServerAddress(address: String) {
        _uiState.value = _uiState.value.copy(serverAddress = address)
    }

    fun saveServerAddress() {
        preferences.edit().putString(SERVER_ADDRESS_KEY, _uiState.value.serverAddress.trim()).apply()
        _uiState.value = _uiState.value.copy(
            serverAddress = _uiState.value.serverAddress.trim(),
            detail = "Server address saved. Connection support is the next milestone.",
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "sendspin_settings"
        const val SERVER_ADDRESS_KEY = "server_address"
    }
}
