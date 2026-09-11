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
}
