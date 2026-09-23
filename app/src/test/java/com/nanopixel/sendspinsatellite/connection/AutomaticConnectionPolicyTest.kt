package com.nanopixel.sendspinsatellite.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticConnectionPolicyTest {
    @Test
    fun `missing last server does not auto connect`() {
        assertFalse(shouldAutoConnect(null, false, ConnectionState.DISCONNECTED))
        assertFalse(shouldAutoConnect("", false, ConnectionState.DISCONNECTED))
    }

    @Test
    fun `saved last server auto connects once while disconnected`() {
        assertTrue(shouldAutoConnect("ws://server/sendspin", false, ConnectionState.DISCONNECTED))
        assertFalse(shouldAutoConnect("ws://server/sendspin", true, ConnectionState.DISCONNECTED))
    }

    @Test
    fun `active session is not replaced by auto connect`() {
        assertFalse(shouldAutoConnect("ws://server/sendspin", false, ConnectionState.CONNECTING))
        assertFalse(shouldAutoConnect("ws://server/sendspin", false, ConnectionState.READY))
        assertFalse(shouldAutoConnect("ws://server/sendspin", false, ConnectionState.BUFFERING))
        assertFalse(shouldAutoConnect("ws://server/sendspin", false, ConnectionState.PLAYING))
        assertTrue(shouldAutoConnect("ws://server/sendspin", false, ConnectionState.ERROR))
    }
}
