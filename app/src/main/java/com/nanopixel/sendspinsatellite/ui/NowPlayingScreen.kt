package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.connection.SavedServer
import com.nanopixel.sendspinsatellite.playback.NowPlayingSnapshot

private val generatedIdentifierSuffix = Regex(
    """\s+\([0-9a-f]{8}-[a-z0-9-]+\)$""",
    RegexOption.IGNORE_CASE,
)

internal data class NowPlayingPresentation(
    val status: String,
    val message: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val emptyState: String?,
    val context: String?,
    val showConnectionProgress: Boolean,
    val showReconnect: Boolean,
    val showDisconnect: Boolean,
)

internal fun shouldShowNowPlaying(state: ConnectionUiState): Boolean {
    if (state.savedServer != null) return true
    return when (state.connectionState) {
        ConnectionState.CONNECTING,
        ConnectionState.HANDSHAKING,
        ConnectionState.SYNCHRONISING,
        ConnectionState.RECOVERING,
        ConnectionState.READY,
        ConnectionState.BUFFERING,
        ConnectionState.PLAYING -> true
        else -> false
    }
}

private fun String?.present(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun ConnectionUiState.toNowPlayingPresentation(): NowPlayingPresentation {
    val title = nowPlaying.title.present()
    val artist = nowPlaying.artist.present() ?: nowPlaying.albumArtist.present()
    val album = nowPlaying.album.present()
    val hasCurrentTrack = title != null || artist != null || album != null ||
        nowPlaying.progress != null
    val paused = hasCurrentTrack && (
        nowPlaying.progress?.playbackSpeedMilli == 0 ||
            nowPlaying.group?.playbackState == NowPlayingSnapshot.PlaybackState.STOPPED
        )
    val status = when {
        connectionState in setOf(
            ConnectionState.CONNECTING,
            ConnectionState.HANDSHAKING,
            ConnectionState.SYNCHRONISING,
        ) -> "Connecting…"
        connectionState == ConnectionState.RECOVERING -> "Recovering…"
        connectionState == ConnectionState.ERROR -> "Couldn't connect"
        connectionState == ConnectionState.DISCONNECTED -> "Playback is stopped"
        paused -> "Paused"
        connectionState == ConnectionState.BUFFERING -> "Buffering"
        connectionState == ConnectionState.PLAYING -> "Playing"
        else -> "Ready"
    }
    val context = listOfNotNull(
        nowPlaying.group?.name.displayName(),
        stateServerName(),
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

    return NowPlayingPresentation(
        status = status,
        message = when (connectionState) {
            ConnectionState.ERROR -> "Check that your server is available, then try again."
            ConnectionState.DISCONNECTED -> "Open settings to change your server or reconnect."
            ConnectionState.CONNECTING,
            ConnectionState.HANDSHAKING,
            ConnectionState.SYNCHRONISING -> "This should only take a moment."
            ConnectionState.RECOVERING -> "We'll keep trying automatically."
            else -> null
        },
        title = title,
        artist = artist,
        album = album,
        emptyState = if (title == null && artist == null && album == null &&
            connectionState in setOf(
                ConnectionState.READY,
                ConnectionState.BUFFERING,
                ConnectionState.PLAYING,
            )
        ) {
            "Ready for playback"
        } else {
            null
        },
        context = context,
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
        showDisconnect = connectionState in setOf(
            ConnectionState.CONNECTING,
            ConnectionState.HANDSHAKING,
            ConnectionState.SYNCHRONISING,
            ConnectionState.RECOVERING,
            ConnectionState.READY,
            ConnectionState.BUFFERING,
            ConnectionState.PLAYING,
        ),
    )
}

private fun ConnectionUiState.stateServerName(): String? {
    return serverName.displayName() ?: savedServer
        ?.takeIf { serverAddress.isBlank() || it.address == serverAddress }
        ?.name
        .displayName()
}

private fun String?.displayName(): String? = present()
    ?.replace(generatedIdentifierSuffix, "")
    .present()

@Composable
internal fun NowPlayingScreen(
    presentation: NowPlayingPresentation,
    onSettings: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sendspin Satellite",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (presentation.showConnectionProgress) {
                CircularProgressIndicator()
            }
            Text(
                presentation.status,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            presentation.title?.let { title ->
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            presentation.artist?.let { artist ->
                Text(
                    artist,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            presentation.album?.let { album ->
                Text(
                    album,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            presentation.emptyState?.let { emptyState ->
                Text(
                    emptyState,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            presentation.context?.let { context ->
                Text(
                    context,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            presentation.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (presentation.showReconnect) {
                Button(onClick = onReconnect) {
                    Text("Connect")
                }
            }
            if (presentation.showDisconnect) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
        }
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
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayingNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                serverAddress = "ws://192.168.1.20:8927/sendspin",
                connectionState = ConnectionState.PLAYING,
                nowPlaying = NowPlayingSnapshot(
                    title = "A track title",
                    artist = "An artist",
                    album = "An album",
                    group = NowPlayingSnapshot.Group(name = "Downstairs"),
                ),
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PartialMetadataNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                connectionState = ConnectionState.PLAYING,
                nowPlaying = NowPlayingSnapshot(
                    title = "A track with no credited artist",
                    albumArtist = "The album artist",
                ),
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                connectionState = ConnectionState.READY,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PausedNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                connectionState = ConnectionState.PLAYING,
                nowPlaying = NowPlayingSnapshot(
                    title = "Paused track",
                    artist = "Artist",
                    progress = NowPlayingSnapshot.Progress(
                        reportedPositionMs = 12_000,
                        durationMs = 180_000,
                        playbackSpeedMilli = 0,
                    ),
                ),
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BufferingNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                connectionState = ConnectionState.BUFFERING,
                nowPlaying = NowPlayingSnapshot(title = "Buffering track"),
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveringNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                connectionState = ConnectionState.RECOVERING,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisconnectedNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                connectionState = ConnectionState.DISCONNECTED,
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun LongMetadataNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                connectionState = ConnectionState.PLAYING,
                serverName = "A server with a deliberately long friendly name",
                nowPlaying = NowPlayingSnapshot(
                    title = "A deliberately long title that should remain readable without taking over the screen",
                    artist = "An artist name that needs an ellipsis at smaller widths",
                    album = "An album name that is longer than the available display width",
                    group = NowPlayingSnapshot.Group(
                        name = "A group name that should remain compact and single line",
                    ),
                ),
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 480)
@Composable
private fun WideNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                savedServer = SavedServer(
                    "ws://192.168.1.20:8927/sendspin",
                    "Music Assistant",
                ),
                connectionState = ConnectionState.PLAYING,
                nowPlaying = NowPlayingSnapshot(
                    title = "Wide layout track",
                    artist = "Artist",
                    album = "Album",
                ),
            ).toNowPlayingPresentation(),
            onSettings = {},
            onReconnect = {},
            onDisconnect = {},
        )
    }
}
