package com.jockgu.sendspinsatellite.connection

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
        assertEquals("Ready", ConnectionState.READY.label)
    }
}
