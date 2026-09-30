package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionPresentationTest {
    @Test
    fun `playing group exposes pause and stop when advertised`() {
        val presentation = status(
            connectionState = ConnectionState.PLAYING,
            groupState = NowPlayingSnapshot.PlaybackState.PLAYING,
            commands = setOf(PlaybackCommand.PLAY, PlaybackCommand.PAUSE, PlaybackCommand.STOP),
        ).toMediaSessionPresentation()

        assertTrue(presentation.active)
        assertEquals(MediaSessionState.PLAYING, presentation.state)
        assertEquals(setOf(PlaybackCommand.PAUSE, PlaybackCommand.STOP), presentation.actions)
    }

    @Test
    fun `stopped group exposes play and preserves paused state`() {
        val presentation = status(
            connectionState = ConnectionState.READY,
            groupState = NowPlayingSnapshot.PlaybackState.STOPPED,
            commands = setOf(PlaybackCommand.PLAY, PlaybackCommand.PAUSE),
        ).toMediaSessionPresentation()

        assertTrue(presentation.active)
        assertEquals(MediaSessionState.PAUSED, presentation.state)
        assertEquals(setOf(PlaybackCommand.PLAY), presentation.actions)
    }

    @Test
    fun `zero metadata speed pauses a playing group`() {
        val presentation = status(
            connectionState = ConnectionState.PLAYING,
            groupState = NowPlayingSnapshot.PlaybackState.PLAYING,
            commands = setOf(PlaybackCommand.PAUSE),
            progress = NowPlayingSnapshot.Progress(20_000, 180_000, 0),
        ).toMediaSessionPresentation()

        assertEquals(MediaSessionState.PAUSED, presentation.state)
        assertEquals(0f, presentation.playbackSpeed)
    }

    @Test
    fun `recovering group remains visible but exposes no actions`() {
        val presentation = status(
            connectionState = ConnectionState.RECOVERING,
            groupState = NowPlayingSnapshot.PlaybackState.PLAYING,
            commands = setOf(PlaybackCommand.PAUSE, PlaybackCommand.STOP),
        ).toMediaSessionPresentation()

        assertTrue(presentation.active)
        assertEquals(MediaSessionState.BUFFERING, presentation.state)
        assertTrue(presentation.actions.isEmpty())
    }

    @Test
    fun `missing group state is inactive and hides actions`() {
        val presentation = PlaybackStatus(
            connectionState = ConnectionState.PLAYING,
            supportedPlaybackCommands = setOf(PlaybackCommand.PAUSE, PlaybackCommand.STOP),
        ).toMediaSessionPresentation()

        assertFalse(presentation.active)
        assertEquals(MediaSessionState.NONE, presentation.state)
        assertTrue(presentation.actions.isEmpty())
    }

    @Test
    fun `metadata uses album artist fallback and bounds progress`() {
        val presentation = PlaybackStatus(
            connectionState = ConnectionState.PLAYING,
            nowPlaying = NowPlayingSnapshot(
                title = "  Track  ",
                artist = " ",
                albumArtist = " Album artist ",
                album = " Album ",
                progress = NowPlayingSnapshot.Progress(
                    reportedPositionMs = 0,
                    durationMs = 10_000,
                    playbackSpeedMilli = 1_250,
                    interpolatedPositionMs = 12_000,
                ),
                group = NowPlayingSnapshot.Group(
                    playbackState = NowPlayingSnapshot.PlaybackState.PLAYING,
                ),
            ),
        ).toMediaSessionPresentation()

        assertEquals("Track", presentation.title)
        assertEquals("Album artist", presentation.artist)
        assertEquals("Album", presentation.album)
        assertEquals(10_000, presentation.positionMs)
        assertEquals(10_000L, presentation.durationMs)
        assertEquals(1.25f, presentation.playbackSpeed)
    }

    @Test
    fun `unknown duration omits duration and never publishes a negative position`() {
        val presentation = status(
            connectionState = ConnectionState.PLAYING,
            groupState = NowPlayingSnapshot.PlaybackState.PLAYING,
            progress = NowPlayingSnapshot.Progress(0, 0, 1_000, -50),
        ).toMediaSessionPresentation()

        assertNull(presentation.durationMs)
        assertEquals(0, presentation.positionMs)
    }

    @Test
    fun `notification identity ignores progress only updates`() {
        val first = status(
            connectionState = ConnectionState.PLAYING,
            groupState = NowPlayingSnapshot.PlaybackState.PLAYING,
            progress = NowPlayingSnapshot.Progress(10_000, 100_000, 1_000, 10_000),
        ).toMediaSessionPresentation()
        val progressOnly = status(
            connectionState = ConnectionState.PLAYING,
            groupState = NowPlayingSnapshot.PlaybackState.PLAYING,
            progress = NowPlayingSnapshot.Progress(11_000, 100_000, 1_000, 11_000),
        ).toMediaSessionPresentation()
        val newArtwork = first.copy(artworkRevision = 2)

        assertEquals(first.notificationIdentity, progressOnly.notificationIdentity)
        assertFalse(first.notificationIdentity == newArtwork.notificationIdentity)
    }

    @Test
    fun `dispatch requires the matching advertised group command while connected`() {
        val playing = status(
            connectionState = ConnectionState.PLAYING,
            groupState = NowPlayingSnapshot.PlaybackState.PLAYING,
            commands = setOf(PlaybackCommand.PLAY, PlaybackCommand.PAUSE, PlaybackCommand.STOP),
        )
        val recovering = playing.copy(connectionState = ConnectionState.RECOVERING)

        assertTrue(playing.canDispatchMediaCommand(PlaybackCommand.PAUSE))
        assertTrue(playing.canDispatchMediaCommand(PlaybackCommand.STOP))
        assertFalse(playing.canDispatchMediaCommand(PlaybackCommand.PLAY))
        assertFalse(recovering.canDispatchMediaCommand(PlaybackCommand.PAUSE))
    }

    private fun status(
        connectionState: ConnectionState,
        groupState: NowPlayingSnapshot.PlaybackState,
        commands: Set<PlaybackCommand> = emptySet(),
        progress: NowPlayingSnapshot.Progress? = null,
    ) = PlaybackStatus(
        connectionState = connectionState,
        nowPlaying = NowPlayingSnapshot(
            progress = progress,
            group = NowPlayingSnapshot.Group(playbackState = groupState),
        ),
        supportedPlaybackCommands = commands,
    )
}