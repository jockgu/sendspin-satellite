package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState

internal data class NowPlayingPresentation(
    val title: String,
    val message: String,
    val serverName: String?,
    val showConnectionProgress: Boolean,
    val showReconnect: Boolean,
)

internal fun shouldShowNowPlaying(state: ConnectionUiState): Boolean {
    if (state.savedServer != null) return true
    return when (state.connectionState) {
        ConnectionState.CONNECTING,
        ConnectionState.HANDSHAKING,
        ConnectionState.SYNCHRONISING,
        ConnectionState.RECOVERING,
        ConnectionState.READY -> true
        else -> false
    }
}

internal fun ConnectionUiState.toNowPlayingPresentation(): NowPlayingPresentation {
    val title = when (connectionState) {
        ConnectionState.READY -> "Ready to play"
        ConnectionState.RECOVERING -> "Reconnecting…"
        ConnectionState.ERROR -> "Couldn't connect"
        ConnectionState.DISCONNECTED -> "Playback is stopped"
        else -> "Connecting…"
    }
    val message = when (connectionState) {
        ConnectionState.READY -> "This device is connected and ready for music."
        ConnectionState.RECOVERING -> "We'll keep trying automatically."
        ConnectionState.ERROR -> "Check that your server is available, then try again."
        ConnectionState.DISCONNECTED -> "Open settings to change your server or reconnect."
        else -> "This should only take a moment."
    }

    return NowPlayingPresentation(
        title = title,
        message = message,
        serverName = savedServer?.name,
        showConnectionProgress = connectionState in setOf(
            ConnectionState.CONNECTING,
            ConnectionState.HANDSHAKING,
            ConnectionState.SYNCHRONISING,
            ConnectionState.RECOVERING,
        ),
        showReconnect = connectionState in setOf(
            ConnectionState.ERROR,
            ConnectionState.DISCONNECTED,
        ),
    )
}

@Composable
internal fun NowPlayingScreen(
    presentation: NowPlayingPresentation,
    onSettings: () -> Unit,
    onReconnect: () -> Unit,
) {
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
            if (presentation.showConnectionProgress) {
                CircularProgressIndicator()
                Spacer(Modifier.height(28.dp))
            }
            Text(presentation.title, style = MaterialTheme.typography.headlineSmall)
            presentation.serverName?.let { name ->
                Spacer(Modifier.height(8.dp))
                Text(name, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                presentation.message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (presentation.showReconnect) {
                Spacer(Modifier.height(28.dp))
                Button(onClick = onReconnect) {
                    Text("Connect")
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectingNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                connectionState = ConnectionState.CONNECTING,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadyNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = com.nanopixel.sendspinsatellite.connection.SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                connectionState = ConnectionState.READY,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveringNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = com.nanopixel.sendspinsatellite.connection.SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                connectionState = ConnectionState.RECOVERING,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisconnectedNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = com.nanopixel.sendspinsatellite.connection.SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                connectionState = ConnectionState.DISCONNECTED,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 480)
@Composable
private fun WideNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = com.nanopixel.sendspinsatellite.connection.SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                connectionState = ConnectionState.READY,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
        )
    }
}