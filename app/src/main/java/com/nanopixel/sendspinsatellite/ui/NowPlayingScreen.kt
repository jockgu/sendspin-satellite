package com.nanopixel.sendspinsatellite.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.connection.SavedServer
import com.nanopixel.sendspinsatellite.playback.ArtworkSnapshot
import com.nanopixel.sendspinsatellite.playback.NowPlayingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val progress: ProgressPresentation?,
    val emptyState: String?,
    val context: String?,
    val showConnectionProgress: Boolean,
    val showReconnect: Boolean,
    val showDisconnect: Boolean,
)

internal data class ProgressPresentation(
    val fraction: Float,
    val elapsedLabel: String,
    val durationLabel: String,
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

internal fun formatDurationMs(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
    } else {
        "${totalSeconds / 60}:$paddedSeconds"
    }
}

internal fun NowPlayingSnapshot.Progress.toPresentation(): ProgressPresentation? {
    if (durationMs <= 0) return null
    val boundedPositionMs = interpolatedPositionMs.coerceIn(0, durationMs)
    val fraction = (boundedPositionMs.toDouble() / durationMs.toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
    return ProgressPresentation(
        fraction = fraction,
        elapsedLabel = formatDurationMs(boundedPositionMs),
        durationLabel = formatDurationMs(durationMs),
    )
}

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
    val progress = nowPlaying.progress
        ?.takeIf {
            connectionState in setOf(
                ConnectionState.READY,
                ConnectionState.BUFFERING,
                ConnectionState.PLAYING,
            )
        }
        ?.toPresentation()
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
        progress = progress,
        emptyState = if (!hasCurrentTrack && connectionState in setOf(
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
    artwork: ArtworkSnapshot = ArtworkSnapshot(),
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
            AlbumArtwork(artwork)
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
            presentation.progress?.let { progress ->
                TrackProgress(progress)
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

@Composable
private fun TrackProgress(progress: ProgressPresentation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Playback progress: ${progress.elapsedLabel} of ${progress.durationLabel}"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                progress.elapsedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                progress.durationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumArtwork(snapshot: ArtworkSnapshot) {
    var bitmap by remember(
        snapshot.revision,
        snapshot.generation,
    ) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(snapshot.revision, snapshot.generation) {
        bitmap = decodeArtwork(snapshot.encodedJpeg)
    }

    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val MAX_ARTWORK_DIMENSION = 512
private const val MAX_ARTWORK_SOURCE_DIMENSION = 8192

internal suspend fun decodeArtwork(encodedJpeg: ByteArray?): Bitmap? = withContext(Dispatchers.Default) {
    val bytes = encodedJpeg ?: return@withContext null
    if (bytes.isEmpty()) return@withContext null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    } catch (_: IllegalArgumentException) {
        return@withContext null
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
        bounds.outWidth > MAX_ARTWORK_SOURCE_DIMENSION ||
        bounds.outHeight > MAX_ARTWORK_SOURCE_DIMENSION
    ) {
        return@withContext null
    }

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > MAX_ARTWORK_DIMENSION ||
        bounds.outHeight / sampleSize > MAX_ARTWORK_DIMENSION
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: IllegalArgumentException) {
        null
    }
    if (bitmap == null || maxOf(bitmap.width, bitmap.height) > MAX_ARTWORK_DIMENSION) {
        bitmap?.recycle()
        return@withContext null
    }
    bitmap
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
                    progress = NowPlayingSnapshot.Progress(
                        reportedPositionMs = 42_000,
                        durationMs = 214_000,
                        playbackSpeedMilli = 1_000,
                        interpolatedPositionMs = 43_000,
                    ),
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
private fun UnknownDurationNowPlayingPreview() {
    MaterialTheme {
        NowPlayingScreen(
            presentation = ConnectionUiState(
                connectionState = ConnectionState.PLAYING,
                nowPlaying = NowPlayingSnapshot(
                    title = "Internet radio",
                    progress = NowPlayingSnapshot.Progress(
                        reportedPositionMs = 12_000,
                        durationMs = 0,
                        playbackSpeedMilli = 1_000,
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
