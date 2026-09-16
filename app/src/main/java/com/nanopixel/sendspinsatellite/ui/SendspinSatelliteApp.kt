package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState

@Composable
fun SendspinSatelliteApp(
    state: ConnectionUiState,
    onServerAddressChanged: (String) -> Unit,
    onPlayerNameChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDiscover: () -> Unit,
    onSelectDiscoveredServer: (String) -> Unit,
    onDisconnect: () -> Unit,
    onForgetServer: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var showForgetConfirmation by rememberSaveable { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                showDiagnostics -> AudioDiagnosticsScreen(
                    diagnostics = state.audioDiagnostics,
                    onBack = {
                        showDiagnostics = false
                        showSettings = true
                    },
                )

                showSettings -> SettingsScreen(
                    state = state,
                    onBack = { showSettings = false },
                    onPlayerNameChanged = onPlayerNameChanged,
                    onDisconnect = {
                        onDisconnect()
                        showSettings = false
                    },
                    onDiscover = {
                        showSettings = false
                        onDiscover()
                    },
                    onManualEntry = { showManualEntry = true },
                    onForgetServer = { showForgetConfirmation = true },
                    onDiagnostics = {
                        showSettings = false
                        showDiagnostics = true
                    },
                )

                shouldShowNowPlaying(state) -> NowPlayingScreen(
                    presentation = state.toNowPlayingPresentation(),
                    onSettings = { showSettings = true },
                    onReconnect = onConnect,
                    onDisconnect = onDisconnect,
                    artwork = state.artwork,
                )

                else -> ConnectionScreen(
                    state = state,
                    onDiscover = onDiscover,
                    onManualEntry = { showManualEntry = true },
                )
            }
        }
    }

    if (state.discoveredServers.size > 1) {
        ServerSelectionDialog(
            servers = state.discoveredServers,
            onSelected = onSelectDiscoveredServer,
            onCancel = onDisconnect,
        )
    }
    if (showManualEntry) {
        ManualServerDialog(
            initialAddress = state.serverAddress,
            onDismiss = { showManualEntry = false },
            onConnect = { address ->
                onServerAddressChanged(address)
                showManualEntry = false
                showSettings = false
                onConnect()
            },
        )
    }
    if (showForgetConfirmation) {
        ForgetServerDialog(
            onDismiss = { showForgetConfirmation = false },
            onConfirm = {
                showForgetConfirmation = false
                showSettings = false
                onForgetServer()
            },
        )
    }
}
