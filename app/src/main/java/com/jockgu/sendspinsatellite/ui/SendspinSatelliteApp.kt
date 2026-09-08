package com.jockgu.sendspinsatellite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jockgu.sendspinsatellite.connection.ConnectionState
import com.jockgu.sendspinsatellite.connection.ConnectionUiState

@Composable
fun SendspinSatelliteApp(
    state: ConnectionUiState,
    onServerAddressChanged: (String) -> Unit,
    onSaveServerAddress: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Sendspin Satellite", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("A dependable Sendspin player for Android.")
                Spacer(Modifier.height(40.dp))

                Text("Player status", style = MaterialTheme.typography.labelLarge)
                Text(state.connectionState.label, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(state.detail, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = state.serverAddress,
                    onValueChange = onServerAddressChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Sendspin server address") },
                    placeholder = { Text("https://sendspin.example") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSaveServerAddress,
                    enabled = state.serverAddress.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save server address")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DisconnectedPreview() {
    SendspinSatelliteApp(
        state = ConnectionUiState(
            serverAddress = "https://sendspin.example",
            connectionState = ConnectionState.DISCONNECTED,
        ),
        onServerAddressChanged = {},
        onSaveServerAddress = {},
    )
}
