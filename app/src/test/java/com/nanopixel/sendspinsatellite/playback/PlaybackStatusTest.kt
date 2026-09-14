package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import org.junit.Assert.assertEquals
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
}
