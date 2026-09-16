package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.connection.SavedServer

@Composable
internal fun SettingsScreen(
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
internal fun ForgetServerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forget this server?") },
        text = { Text("Sendspin Satellite will search for a server again next time it opens.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Forget server")
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
private fun SettingsPreview() {
    MaterialTheme {
        SettingsScreen(
            state = ConnectionUiState(
                savedServer = SavedServer("ws://192.168.1.20:8927/sendspin", "Music Assistant"),
                connectionState = ConnectionState.READY,
            ),
            onBack = {},
            onPlayerNameChanged = {},
            onDisconnect = {},
            onDiscover = {},
            onManualEntry = {},
            onForgetServer = {},
            onDiagnostics = {},
        )
    }
}