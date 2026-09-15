package com.nanopixel.sendspinsatellite.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressPolicyTest {
    @Test
    fun `ip address and hostname use the default Sendspin endpoint`() {
        assertEquals(
            "ws://192.168.1.20:8927/sendspin",
            normalizeServerAddress("192.168.1.20"),
        )
        assertEquals(
            "ws://music-server.local:8927/sendspin",
            normalizeServerAddress("music-server.local"),
        )
    }

    @Test
    fun `full websocket address is preserved`() {
        assertEquals(
            "ws://music-server.local:9000/custom-sendspin",
            normalizeServerAddress("ws://music-server.local:9000/custom-sendspin"),
        )
    }

    @Test
    fun `invalid address is rejected`() {
        assertNull(normalizeServerAddress(""))
        assertNull(normalizeServerAddress("https://music-server.local"))
        assertNull(normalizeServerAddress("music server"))
    }
}
