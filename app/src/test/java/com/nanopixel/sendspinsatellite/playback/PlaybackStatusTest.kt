package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatusTest {
    @Test
    fun `service status preserves the configured address for the UI`() {
        val uiState = PlaybackStatus(connectionState = ConnectionState.READY).toUiState("ws://server/sendspin")

        assertEquals("ws://server/sendspin", uiState.serverAddress)
        assertEquals(ConnectionState.READY, uiState.connectionState)
    }
}
