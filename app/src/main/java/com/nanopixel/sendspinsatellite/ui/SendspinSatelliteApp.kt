package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.playback.PlaybackCommand

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
    onPlaybackCommand: (PlaybackCommand) -> Unit,
    onConfirmPairingWindow: () -> Unit,
    onCancelPairingWindow: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var showForgetConfirmation by rememberSaveable { mutableStateOf(false) }
    var dismissedPairingRevision by rememberSaveable { mutableStateOf(-1L) }

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
                    onPlaybackCommand = onPlaybackCommand,
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
    val pairingVisible = state.pairingActive || state.pairingCode != null ||
        state.pairingWindowOpen || state.pairingFailed
    if (pairingVisible && dismissedPairingRevision != state.pairingRevision) {
        AlertDialog(
            onDismissRequest = { dismissedPairingRevision = state.pairingRevision },
            title = {
                Text(when {
                    state.pairingFailed -> "Sendspin pairing failed"
                    state.pairingWindowOpen -> "Allow Sendspin pairing?"
                    else -> "Pairing with Sendspin"
                })
            },
            text = {
                Column {
                    if (state.pairingFailed) {
                        Text(when (state.pairingFailureReason) {
                            0 -> "The pairing attempt timed out. Start pairing again from Music Assistant."
                            1 -> "Another pairing attempt is already active. Try again in a moment."
                            2 -> "The server selected an unsupported pairing method. Update Music Assistant."
                            3 -> "The pairing codes did not match. Start pairing again from Music Assistant."
                            4 -> "The pairing attempt was cancelled."
                            else -> "Pairing could not be completed. Try again from Music Assistant."
                        })
                    } else {
                        if (state.pairingWindowOpen) Text(
                            "Confirm on this device to allow the server's pairing attempt to continue.",
                        )
                        if (state.pairingCode != null) {
                            Spacer(Modifier.height(12.dp))
                            Text("Enter this code on your Music Assistant server:")
                            Spacer(Modifier.height(12.dp))
                            Text(
                                state.pairingCode.chunked(3).joinToString("-"),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        } else if (!state.pairingWindowOpen) {
                            Text("Waiting for the server to complete secure pairing.")
                        }
                    }
                }
            },
            confirmButton = {
                if (state.pairingWindowOpen && !state.pairingFailed) {
                    Button(onClick = onConfirmPairingWindow) { Text("Allow pairing") }
                } else {
                    TextButton(onClick = { dismissedPairingRevision = state.pairingRevision }) {
                        Text("Done")
                    }
                }
            },
            dismissButton = if (state.pairingWindowOpen && !state.pairingFailed) {
                {
                    TextButton(onClick = {
                        dismissedPairingRevision = state.pairingRevision
                        onCancelPairingWindow()
                    }) { Text("Cancel") }
                }
            } else {
                {}
            },
        )
    }
}
