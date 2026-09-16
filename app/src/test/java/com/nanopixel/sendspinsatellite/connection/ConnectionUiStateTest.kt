package com.nanopixel.sendspinsatellite.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionUiStateTest {
    @Test
    fun `new session starts disconnected`() {
        assertEquals(ConnectionState.DISCONNECTED, ConnectionUiState().connectionState)
    }

    @Test
    fun `states use concise player-facing labels`() {
        assertEquals("Synchronising", ConnectionState.SYNCHRONISING.label)
        assertEquals("Synchronised", ConnectionState.READY.label)
        assertEquals("Buffering", ConnectionState.BUFFERING.label)
        assertEquals("Playing", ConnectionState.PLAYING.label)
    }
}
