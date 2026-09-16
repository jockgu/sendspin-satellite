package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.connection.SavedServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStatusTest {
    @Test
    fun `service status preserves the configured address for the UI`() {
        val uiState = PlaybackStatus(connectionState = ConnectionState.READY).toUiState(
            ConnectionUiState(
                serverAddress = "ws://server/sendspin",
                playerName = "Kitchen Speaker",
            ),
        )

        assertEquals("ws://server/sendspin", uiState.serverAddress)
        assertEquals("Kitchen Speaker", uiState.playerName)
        assertEquals(ConnectionState.READY, uiState.connectionState)
    }

    @Test
    fun `service status carries discovered servers without replacing manual address`() {
        val server = DiscoveredServer("ha", "Home Assistant", "ws://127.0.0.1:8927/sendspin")

        val uiState = PlaybackStatus(
            connectionState = ConnectionState.DISCOVERING,
            discoveredServers = listOf(server),
        ).toUiState(ConnectionUiState(serverAddress = "ws://manual/sendspin"))

        assertEquals(listOf(server), uiState.discoveredServers)
        assertEquals("ws://manual/sendspin", uiState.serverAddress)
    }

    @Test
    fun `service status carries the selected server name for the player screen`() {
        val uiState = PlaybackStatus(
            connectionState = ConnectionState.READY,
            server = SavedServer("ws://192.168.1.20:8927/sendspin", "Music Assistant"),
        ).toUiState(ConnectionUiState())

        assertEquals("Music Assistant", uiState.serverName)
    }

    @Test
    fun `active server without a friendly name clears a stale server name`() {
        val uiState = PlaybackStatus(
            connectionState = ConnectionState.CONNECTING,
            server = SavedServer("ws://new/sendspin"),
        ).toUiState(
            ConnectionUiState(
                serverName = "Old server",
                savedServer = SavedServer("ws://old/sendspin", "Old server"),
            ),
        )

        assertNull(uiState.serverName)
    }

    @Test
    fun `service status carries now playing snapshot unchanged`() {
        val nowPlaying = NowPlayingSnapshot(
            revision = 8,
            generation = 3,
            title = "Track title",
            artist = "Artist",
            albumArtist = "Album artist",
            album = "Album",
            progress = NowPlayingSnapshot.Progress(
                reportedPositionMs = 12_345,
                durationMs = 234_567,
                playbackSpeedMilli = 1_000,
            ),
            group = NowPlayingSnapshot.Group(
                name = "Downstairs",
                playbackState = NowPlayingSnapshot.PlaybackState.PLAYING,
            ),
        )

        val uiState = PlaybackStatus(nowPlaying = nowPlaying).toUiState(ConnectionUiState())

        assertEquals(nowPlaying, uiState.nowPlaying)
    }

    @Test
    fun `service status carries artwork snapshot unchanged`() {
        val artwork = ArtworkSnapshot(
            revision = 4,
            generation = 2,
            encodedJpeg = byteArrayOf(1, 2, 3),
        )

        val uiState = PlaybackStatus(artwork = artwork).toUiState(ConnectionUiState())

        assertEquals(artwork.revision, uiState.artwork.revision)
        assertEquals(artwork.generation, uiState.artwork.generation)
        assertArrayEquals(artwork.encodedJpeg, uiState.artwork.encodedJpeg)
    }

    @Test
    fun `fresh service status clears previous now playing without replacing configuration`() {
        val current = ConnectionUiState(
            serverAddress = "ws://server/sendspin",
            playerName = "Kitchen Speaker",
            nowPlaying = NowPlayingSnapshot(title = "Old track"),
        )

        val uiState = PlaybackStatus().toUiState(current)

        assertEquals(NowPlayingSnapshot(), uiState.nowPlaying)
        assertEquals(ArtworkSnapshot(), uiState.artwork)
        assertEquals("ws://server/sendspin", uiState.serverAddress)
        assertEquals("Kitchen Speaker", uiState.playerName)
    }
}
