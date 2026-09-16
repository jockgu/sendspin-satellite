package com.nanopixel.sendspinsatellite.ui

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.connection.SavedServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `ready presentation preserves shell text and saved server name`() {
        val presentation = ConnectionUiState(
            savedServer = SavedServer("ws://server/sendspin", "Music Assistant"),
            connectionState = ConnectionState.READY,
        ).toNowPlayingPresentation()

        assertEquals(
            NowPlayingPresentation(
                title = "Ready to play",
                message = "This device is connected and ready for music.",
                serverName = "Music Assistant",
                showConnectionProgress = false,
                showReconnect = false,
            ),
            presentation,
        )
    }

    @Test
    fun `connecting presentation shows connection progress`() {
        assertEquals(
            NowPlayingPresentation(
                title = "Connecting…",
                message = "This should only take a moment.",
                serverName = null,
                showConnectionProgress = true,
                showReconnect = false,
            ),
            ConnectionUiState(connectionState = ConnectionState.CONNECTING)
                .toNowPlayingPresentation(),
        )
    }

    @Test
    fun `recovering presentation shows connection progress without reconnect action`() {
        assertEquals(
            NowPlayingPresentation(
                title = "Reconnecting…",
                message = "We'll keep trying automatically.",
                serverName = null,
                showConnectionProgress = true,
                showReconnect = false,
            ),
            ConnectionUiState(connectionState = ConnectionState.RECOVERING)
                .toNowPlayingPresentation(),
        )
    }

    @Test
    fun `error presentation shows reconnect action`() {
        assertEquals(
            NowPlayingPresentation(
                title = "Couldn't connect",
                message = "Check that your server is available, then try again.",
                serverName = null,
                showConnectionProgress = false,
                showReconnect = true,
            ),
            ConnectionUiState(connectionState = ConnectionState.ERROR)
                .toNowPlayingPresentation(),
        )
    }

    @Test
    fun `disconnected presentation shows reconnect action`() {
        assertEquals(
            NowPlayingPresentation(
                title = "Playback is stopped",
                message = "Open settings to change your server or reconnect.",
                serverName = null,
                showConnectionProgress = false,
                showReconnect = true,
            ),
            ConnectionUiState(connectionState = ConnectionState.DISCONNECTED)
                .toNowPlayingPresentation(),
        )
    }
}