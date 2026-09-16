package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
internal fun ConnectionScreen(
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
internal fun ServerSelectionDialog(
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
internal fun ManualServerDialog(
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
private fun DisconnectedConnectionPreview() {
    MaterialTheme {
        ConnectionScreen(
            state = ConnectionUiState(),
            onDiscover = {},
            onManualEntry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoveringConnectionPreview() {
    MaterialTheme {
        ConnectionScreen(
            state = ConnectionUiState(connectionState = ConnectionState.DISCOVERING),
            onDiscover = {},
            onManualEntry = {},
        )
    }
}