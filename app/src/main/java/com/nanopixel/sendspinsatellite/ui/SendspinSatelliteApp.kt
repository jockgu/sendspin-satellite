package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.connection.ConnectionState
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
) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    MaterialTheme {
        if (showDiagnostics) {
            AudioDiagnosticsScreen(
                diagnostics = state.audioDiagnostics,
                onBack = { showDiagnostics = false },
            )
        } else {
            Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Sendspin Satellite", style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = { showDiagnostics = true }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "Audio diagnostics",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("A dependable Sendspin player for Android.")
                Spacer(Modifier.height(40.dp))

                Text("Player status", style = MaterialTheme.typography.labelLarge)
                Text(state.connectionState.label, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(state.detail, style = MaterialTheme.typography.bodyMedium)
                state.serverName?.let { serverName ->
                    Spacer(Modifier.height(8.dp))
                    Text("Server: $serverName", style = MaterialTheme.typography.bodyMedium)
                }
                if (state.clockSamples > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Clock: ${state.clockSamples} samples · RTT ${formatMilliseconds(state.roundTripUs)} · offset ${formatMilliseconds(state.clockOffsetUs)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = state.playerName,
                    onValueChange = onPlayerNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = state.playerNameError != null,
                    label = { Text("Player name") },
                    supportingText = {
                        Text(state.playerNameError ?: "Shown to the Sendspin server; applies on the next connection.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.serverAddress,
                    onValueChange = onServerAddressChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Sendspin server address") },
                    placeholder = { Text("ws://server.local:8927/sendspin") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                if (state.connectionState == ConnectionState.DISCONNECTED &&
                    state.serverAddress.isNotBlank()
                ) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The saved server is ready to connect. You can edit it or find another local server.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onDiscover,
                    enabled = state.connectionState == ConnectionState.DISCONNECTED ||
                        state.connectionState == ConnectionState.ERROR,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Find local server")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = if (state.connectionState == ConnectionState.DISCONNECTED || state.connectionState == ConnectionState.ERROR) onConnect else onDisconnect,
                    enabled = state.serverAddress.isNotBlank() || state.connectionState != ConnectionState.DISCONNECTED,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.connectionState == ConnectionState.DISCONNECTED || state.connectionState == ConnectionState.ERROR) {
                            "Connect"
                        } else if (state.connectionState == ConnectionState.DISCOVERING) {
                            "Cancel"
                        } else {
                            "Disconnect"
                        },
                    )
                }
            }
        }
        }
    }
    if (state.discoveredServers.size > 1) {
        AlertDialog(
            onDismissRequest = onDisconnect,
            title = { Text("Choose a Sendspin server") },
            text = {
                Column {
                    state.discoveredServers.forEach { server ->
                        TextButton(
                            onClick = { onSelectDiscoveredServer(server.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${server.name}\n${server.url}")
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisconnectedPreview() {
    SendspinSatelliteApp(
        state = ConnectionUiState(
            serverAddress = "ws://server.local:8927/sendspin",
            connectionState = ConnectionState.DISCONNECTED,
        ),
        onServerAddressChanged = {},
            onPlayerNameChanged = {},
        onConnect = {},
        onDiscover = {},
        onSelectDiscoveredServer = {},
        onDisconnect = {},
    )
}

private fun formatMilliseconds(valueUs: Long?): String = valueUs?.let { "%.2f ms".format(it / 1_000.0) } ?: "—"
