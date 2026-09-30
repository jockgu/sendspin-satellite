package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.connection.ConnectionState

internal enum class MediaSessionState {
    NONE,
    BUFFERING,
    PLAYING,
    PAUSED,
}

internal data class MediaSessionPresentation(
    val connectionState: ConnectionState,
    val active: Boolean,
    val state: MediaSessionState,
    val actions: Set<PlaybackCommand>,
    val title: String?,
    val artist: String?,
    val album: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val playbackSpeed: Float,
    val artworkGeneration: Long,
    val artworkRevision: Long,
) {
    val notificationIdentity: NotificationIdentity
        get() = NotificationIdentity(
            connectionState = connectionState,
            active = active,
            state = state,
            actions = actions,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkGeneration = artworkGeneration,
            artworkRevision = artworkRevision,
        )
}

internal data class NotificationIdentity(
    val connectionState: ConnectionState,
    val active: Boolean,
    val state: MediaSessionState,
    val actions: Set<PlaybackCommand>,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val artworkGeneration: Long,
    val artworkRevision: Long,
)

internal fun PlaybackStatus.toMediaSessionPresentation(): MediaSessionPresentation {
    val groupState = nowPlaying.group?.playbackState
    val active = groupState != null && connectionState in setOf(
        ConnectionState.READY,
        ConnectionState.BUFFERING,
        ConnectionState.PLAYING,
        ConnectionState.RECOVERING,
    )
    val commandStateIsStable = connectionState in setOf(
        ConnectionState.READY,
        ConnectionState.BUFFERING,
        ConnectionState.PLAYING,
    )
    val actions = if (active && commandStateIsStable) {
        supportedPlaybackCommands.filterTo(mutableSetOf()) { command ->
            command == PlaybackCommand.STOP || command == groupState.primaryCommand()
        }
    } else {
        emptySet()
    }
    val progress = nowPlaying.progress
    val durationMs = progress?.durationMs?.takeIf { it > 0 }
    val positionMs = progress?.interpolatedPositionMs
        ?.coerceIn(0, durationMs ?: Long.MAX_VALUE)
        ?: 0L
    val playbackSpeed = when {
        groupState == NowPlayingSnapshot.PlaybackState.STOPPED -> 0f
        progress?.playbackSpeedMilli == null -> 1f
        progress.playbackSpeedMilli <= 0 -> 0f
        else -> progress.playbackSpeedMilli / 1_000f
    }
    val state = when {
        !active -> MediaSessionState.NONE
        connectionState in setOf(ConnectionState.BUFFERING, ConnectionState.RECOVERING) ->
            MediaSessionState.BUFFERING
        groupState == NowPlayingSnapshot.PlaybackState.STOPPED || playbackSpeed == 0f ->
            MediaSessionState.PAUSED
        else -> MediaSessionState.PLAYING
    }

    return MediaSessionPresentation(
        connectionState = connectionState,
        active = active,
        state = state,
        actions = actions,
        title = nowPlaying.title.present(),
        artist = nowPlaying.artist.present() ?: nowPlaying.albumArtist.present(),
        album = nowPlaying.album.present(),
        positionMs = positionMs,
        durationMs = durationMs,
        playbackSpeed = playbackSpeed,
        artworkGeneration = artwork.generation,
        artworkRevision = artwork.revision,
    )
}

internal fun PlaybackStatus.canDispatchMediaCommand(command: PlaybackCommand): Boolean {
    if (connectionState !in setOf(
            ConnectionState.READY,
            ConnectionState.BUFFERING,
            ConnectionState.PLAYING,
        )
    ) {
        return false
    }
    val groupState = nowPlaying.group?.playbackState ?: return false
    if (command !in supportedPlaybackCommands) return false
    return command == PlaybackCommand.STOP || command == groupState.primaryCommand()
}

private fun NowPlayingSnapshot.PlaybackState.primaryCommand(): PlaybackCommand = when (this) {
    NowPlayingSnapshot.PlaybackState.PLAYING -> PlaybackCommand.PAUSE
    NowPlayingSnapshot.PlaybackState.STOPPED -> PlaybackCommand.PLAY
}

private fun String?.present(): String? = this?.trim()?.takeIf { it.isNotEmpty() }