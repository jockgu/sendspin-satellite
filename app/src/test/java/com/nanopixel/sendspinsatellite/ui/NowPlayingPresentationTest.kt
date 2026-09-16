package com.nanopixel.sendspinsatellite.ui

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.connection.SavedServer
import com.nanopixel.sendspinsatellite.playback.NowPlayingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingPresentationTest {
    @Test
    fun `first-run disconnected and discovering states stay on setup`() {
        assertFalse(shouldShowNowPlaying(ConnectionUiState()))
        assertFalse(
            shouldShowNowPlaying(
                ConnectionUiState(connectionState = ConnectionState.DISCOVERING),
            ),
        )
    }

    @Test
    fun `active connection states show now-playing before a server is saved`() {
        listOf(
            ConnectionState.CONNECTING,
            ConnectionState.HANDSHAKING,
            ConnectionState.SYNCHRONISING,
            ConnectionState.RECOVERING,
            ConnectionState.READY,
            ConnectionState.BUFFERING,
            ConnectionState.PLAYING,
        ).forEach { connectionState ->
            assertTrue(
                connectionState.name,
                shouldShowNowPlaying(ConnectionUiState(connectionState = connectionState)),
            )
        }
    }

    @Test
    fun `saved server keeps now-playing visible when disconnected or in error`() {
        val savedServer = SavedServer("ws://server/sendspin", "Music Assistant")

        assertTrue(
            shouldShowNowPlaying(
                ConnectionUiState(
                    savedServer = savedServer,
                    connectionState = ConnectionState.DISCONNECTED,
                ),
            ),
        )
        assertTrue(
            shouldShowNowPlaying(
                ConnectionUiState(
                    savedServer = savedServer,
                    connectionState = ConnectionState.ERROR,
                ),
            ),
        )
    }

    @Test
    fun `complete metadata maps to playing presentation with friendly context`() {
        val presentation = ConnectionUiState(
            savedServer = SavedServer("ws://server/sendspin", "Music Assistant"),
            serverAddress = "ws://server/sendspin",
            connectionState = ConnectionState.PLAYING,
            nowPlaying = NowPlayingSnapshot(
                title = "Track title",
                artist = "Artist",
                album = "Album",
                group = NowPlayingSnapshot.Group(name = "Downstairs"),
            ),
        ).toNowPlayingPresentation()

        assertEquals("Playing", presentation.status)
        assertEquals("Track title", presentation.title)
        assertEquals("Artist", presentation.artist)
        assertEquals("Album", presentation.album)
        assertEquals("Downstairs · Music Assistant", presentation.context)
        assertNull(presentation.emptyState)
        assertFalse(presentation.showConnectionProgress)
        assertTrue(presentation.showDisconnect)
    }

    @Test
    fun `album artist is used only when artist is absent`() {
        val withFallback = ConnectionUiState(
            connectionState = ConnectionState.READY,
            nowPlaying = NowPlayingSnapshot(
                title = "Track",
                albumArtist = "Album artist",
            ),
        ).toNowPlayingPresentation()
        assertEquals("Album artist", withFallback.artist)

        val withArtist = ConnectionUiState(
            connectionState = ConnectionState.READY,
            nowPlaying = NowPlayingSnapshot(
                title = "Track",
                artist = "Track artist",
                albumArtist = "Album artist",
            ),
        ).toNowPlayingPresentation()
        assertEquals("Track artist", withArtist.artist)
    }

    @Test
    fun `blank metadata rows collapse and ready has a stable empty state`() {
        val presentation = ConnectionUiState(
            connectionState = ConnectionState.READY,
            nowPlaying = NowPlayingSnapshot(
                title = "  ",
                artist = "\t",
                albumArtist = "",
                album = " ",
            ),
        ).toNowPlayingPresentation()

        assertNull(presentation.title)
        assertNull(presentation.artist)
        assertNull(presentation.album)
        assertEquals("Ready", presentation.status)
        assertEquals("Ready for playback", presentation.emptyState)
    }

    @Test
    fun `zero metadata speed reports paused without rendering progress`() {
        val presentation = ConnectionUiState(
            connectionState = ConnectionState.PLAYING,
            nowPlaying = NowPlayingSnapshot(
                title = "Paused track",
                progress = NowPlayingSnapshot.Progress(
                    reportedPositionMs = 12_000,
                    durationMs = 180_000,
                    playbackSpeedMilli = 0,
                ),
            ),
        ).toNowPlayingPresentation()

        assertEquals("Paused", presentation.status)
        assertEquals("Paused track", presentation.title)
        assertNull(presentation.emptyState)
    }

    @Test
    fun `stopped group with a current track reports paused`() {
        val presentation = ConnectionUiState(
            connectionState = ConnectionState.PLAYING,
            nowPlaying = NowPlayingSnapshot(
                title = "Stopped group track",
                group = NowPlayingSnapshot.Group(
                    name = "Kitchen",
                    playbackState = NowPlayingSnapshot.PlaybackState.STOPPED,
                ),
            ),
        ).toNowPlayingPresentation()

        assertEquals("Paused", presentation.status)
    }

    @Test
    fun `buffering and playing statuses preserve their native distinction`() {
        assertEquals(
            "Buffering",
            ConnectionUiState(connectionState = ConnectionState.BUFFERING)
                .toNowPlayingPresentation()
                .status,
        )
        assertEquals(
            "Playing",
            ConnectionUiState(connectionState = ConnectionState.PLAYING)
                .toNowPlayingPresentation()
                .status,
        )
    }

    @Test
    fun `connection and recovery presentations retain actions and messages`() {
        val connecting = ConnectionUiState(
            connectionState = ConnectionState.CONNECTING,
        ).toNowPlayingPresentation()
        assertEquals("Connecting…", connecting.status)
        assertEquals("This should only take a moment.", connecting.message)
        assertTrue(connecting.showConnectionProgress)
        assertTrue(connecting.showDisconnect)
        assertFalse(connecting.showReconnect)

        val recovering = ConnectionUiState(
            connectionState = ConnectionState.RECOVERING,
        ).toNowPlayingPresentation()
        assertEquals("Recovering…", recovering.status)
        assertTrue(recovering.showConnectionProgress)
        assertTrue(recovering.showDisconnect)

        val error = ConnectionUiState(
            connectionState = ConnectionState.ERROR,
        ).toNowPlayingPresentation()
        assertEquals("Couldn't connect", error.status)
        assertTrue(error.showReconnect)
        assertFalse(error.showDisconnect)

        val disconnected = ConnectionUiState(
            connectionState = ConnectionState.DISCONNECTED,
        ).toNowPlayingPresentation()
        assertEquals("Playback is stopped", disconnected.status)
        assertTrue(disconnected.showReconnect)
        assertFalse(disconnected.showDisconnect)
    }

    @Test
    fun `context omits absent names and does not fall back to another server`() {
        val groupOnly = ConnectionUiState(
            connectionState = ConnectionState.READY,
            nowPlaying = NowPlayingSnapshot(
                group = NowPlayingSnapshot.Group(name = "Kitchen"),
            ),
        ).toNowPlayingPresentation()
        assertEquals("Kitchen", groupOnly.context)

        val serverOnly = ConnectionUiState(
            serverName = "Music Assistant",
            connectionState = ConnectionState.READY,
        ).toNowPlayingPresentation()
        assertEquals("Music Assistant", serverOnly.context)

        val differentManualServer = ConnectionUiState(
            savedServer = SavedServer("ws://old/sendspin", "Old server"),
            serverAddress = "ws://new/sendspin",
            connectionState = ConnectionState.CONNECTING,
        ).toNowPlayingPresentation()
        assertNull(differentManualServer.context)
    }

    @Test
    fun `context removes the generated identifier suffix from friendly names`() {
        val presentation = ConnectionUiState(
            serverName = "Music Assistant (d5369777-music-assistant)",
            connectionState = ConnectionState.READY,
            nowPlaying = NowPlayingSnapshot(
                group = NowPlayingSnapshot.Group(name = "Kitchen"),
            ),
        ).toNowPlayingPresentation()

        assertEquals("Kitchen · Music Assistant", presentation.context)
    }
}
