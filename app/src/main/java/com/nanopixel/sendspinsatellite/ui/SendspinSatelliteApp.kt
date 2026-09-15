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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.playback.DiscoveredServer

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

                state.savedServer == null -> SetupScreen(
                    state = state,
                    onDiscover = onDiscover,
                    onManualEntry = { showManualEntry = true },
                )

                else -> PlayerScreen(
                    state = state,
                    onSettings = { showSettings = true },
                    onReconnect = onConnect,
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
        AlertDialog(
            onDismissRequest = { showForgetConfirmation = false },
            title = { Text("Forget this server?") },
            text = { Text("Sendspin Satellite will search for a server again next time it opens.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgetConfirmation = false
                        showSettings = false
                        onForgetServer()
                    },
                ) {
                    Text("Forget server")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SetupScreen(
    state: ConnectionUiState,
    onDiscover: () -> Unit,
    onManualEntry: () -> Unit,
) {
    val inProgress = state.connectionState in setOf(
        ConnectionState.DISCOVERING,
        ConnectionState.CONNECTING,
        ConnectionState.HANDSHAKING,
        ConnectionState.SYNCHRONISING,
    )
    val title = when (state.connectionState) {
        ConnectionState.DISCOVERING -> "Finding your Sendspin system…"
        ConnectionState.CONNECTING,
        ConnectionState.HANDSHAKING,
        ConnectionState.SYNCHRONISING -> "Connecting to your Sendspin system…"
        ConnectionState.ERROR -> "We couldn't connect"
        else -> "Set up Sendspin Satellite"
    }
    val message = when (state.connectionState) {
        ConnectionState.DISCOVERING -> "Looking on your local network."
        ConnectionState.CONNECTING,
        ConnectionState.HANDSHAKING,
        ConnectionState.SYNCHRONISING -> "This should only take a moment."
        ConnectionState.ERROR -> state.detail
        else -> "Find your server automatically, or set it up manually."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (inProgress) {
            CircularProgressIndicator()
            Spacer(Modifier.height(28.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (!inProgress) {
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDiscover, modifier = Modifier.fillMaxWidth()) {
                Text("Find a server")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) {
                Text("Set up manually")
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    state: ConnectionUiState,
    onSettings: () -> Unit,
    onReconnect: () -> Unit,
) {
    val connecting = state.connectionState in setOf(
        ConnectionState.CONNECTING,
        ConnectionState.HANDSHAKING,
        ConnectionState.SYNCHRONISING,
        ConnectionState.RECOVERING,
    )
    val title = when (state.connectionState) {
        ConnectionState.READY -> "Ready to play"
        ConnectionState.RECOVERING -> "Reconnecting…"
        ConnectionState.ERROR -> "Couldn't connect"
        ConnectionState.DISCONNECTED -> "Playback is stopped"
        else -> "Connecting…"
    }
    val message = when (state.connectionState) {
        ConnectionState.READY -> "This device is connected and ready for music."
        ConnectionState.RECOVERING -> "We'll keep trying automatically."
        ConnectionState.ERROR -> "Check that your server is available, then try again."
        ConnectionState.DISCONNECTED -> "Open settings to change your server or reconnect."
        else -> "This should only take a moment."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sendspin Satellite", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (connecting) {
                CircularProgressIndicator()
                Spacer(Modifier.height(28.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            state.savedServer?.name?.let { name ->
                Spacer(Modifier.height(8.dp))
                Text(name, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (state.connectionState in setOf(ConnectionState.ERROR, ConnectionState.DISCONNECTED)) {
                Spacer(Modifier.height(28.dp))
                Button(onClick = onReconnect) {
                    Text("Connect")
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SettingsScreen(
    state: ConnectionUiState,
    onBack: () -> Unit,
    onPlayerNameChanged: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDiscover: () -> Unit,
    onManualEntry: () -> Unit,
    onForgetServer: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(32.dp))

        Text("Player name", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.playerName,
            onValueChange = onPlayerNameChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state.playerNameError != null,
            supportingText = {
                Text(state.playerNameError ?: "Shown to your Sendspin server on the next connection.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Spacer(Modifier.height(32.dp))

        Text("Server", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(state.savedServer?.name ?: "Preferred server", style = MaterialTheme.typography.titleMedium)
        Text(state.savedServer?.address.orEmpty(), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDiscover, modifier = Modifier.fillMaxWidth()) {
            Text("Find a different server")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) {
            Text("Enter server address")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDisconnect,
            enabled = state.connectionState != ConnectionState.DISCONNECTED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Disconnect")
        }
        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Audio diagnostics")
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onForgetServer, modifier = Modifier.fillMaxWidth()) {
            Text("Forget server")
        }
    }
}

@Composable
private fun ServerSelectionDialog(
    servers: List<DiscoveredServer>,
    onSelected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val duplicateNames = servers.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Choose your Sendspin server") },
        text = {
            Column {
                servers.forEach { server ->
                    TextButton(
                        onClick = { onSelected(server.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(server.name, textAlign = TextAlign.Start)
                            if (server.name in duplicateNames) {
                                Text(
                                    server.url.removePrefix("ws://").removeSuffix("/sendspin"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ManualServerDialog(
    initialAddress: String,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
) {
    var address by rememberSaveable(initialAddress) { mutableStateOf(initialAddress) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter server address") },
        text = {
            Column {
                Text("Enter an IP address or hostname. For a custom port or path, enter the full ws:// address.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(address) }) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PlayerPreview() {
    SendspinSatelliteApp(
        state = ConnectionUiState(
            savedServer = com.nanopixel.sendspinsatellite.connection.SavedServer(
                "ws://192.168.1.20:8927/sendspin",
                "Music Assistant",
            ),
            connectionState = ConnectionState.READY,
        ),
        onServerAddressChanged = {},
        onPlayerNameChanged = {},
        onConnect = {},
        onDiscover = {},
        onSelectDiscoveredServer = {},
        onDisconnect = {},
        onForgetServer = {},
    )
}
