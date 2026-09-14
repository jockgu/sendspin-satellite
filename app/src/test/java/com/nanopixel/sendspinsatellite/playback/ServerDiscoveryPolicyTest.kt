package com.nanopixel.sendspinsatellite.playback

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDiscoveryPolicyTest {
    @Test
    fun `client initiated discovery browses the server service type`() {
        assertEquals("_sendspin-server._tcp", SendspinServerDiscovery.SERVICE_TYPE)
    }

    @Test
    fun `no server leaves the manual path available`() {
        assertTrue(decideServerDiscovery(emptyList()) is ServerDiscoveryDecision.None)
    }

    @Test
    fun `one server is selected automatically`() {
        val server = server("one", "ws://127.0.0.1:8927/sendspin")

        val decision = decideServerDiscovery(listOf(server))

        assertEquals(ServerDiscoveryDecision.AutoConnect(server), decision)
    }

    @Test
    fun `multiple servers are presented for selection and duplicate ids collapse`() {
        val first = server("one", "ws://127.0.0.1:8927/sendspin")
        val second = server("two", "ws://127.0.0.2:8927/sendspin")

        val decision = decideServerDiscovery(listOf(first, first, second))

        assertEquals(ServerDiscoveryDecision.Select(listOf(first, second)), decision)
    }

    @Test
    fun `url builder formats ipv4 and ipv6 and rejects invalid ports`() {
        assertEquals(
            "ws://127.0.0.1:8927/sendspin",
            sendspinWebSocketUrl(InetAddress.getByName("127.0.0.1"), 8927),
        )
        val ipv6Url = sendspinWebSocketUrl(InetAddress.getByName("::1"), 8927).orEmpty()
        assertTrue(ipv6Url.startsWith("ws://["))
        assertTrue(ipv6Url.endsWith("]:8927/sendspin"))
        assertEquals(null, sendspinWebSocketUrl(InetAddress.getByName("127.0.0.1"), 0))
    }

    @Test
    fun `discovery prefers ipv4 when android returns ipv6 first`() {
        val ipv6 = InetAddress.getByName("fe80::1")
        val ipv4 = InetAddress.getByName("192.168.1.20")

        assertEquals(ipv4, preferredSendspinHost(listOf(ipv6, ipv4)))
        assertEquals(ipv6, preferredSendspinHost(listOf(ipv6)))
    }

    @Test
    fun `server metadata requires path and uses its friendly name`() {
        assertEquals(
            SendspinDiscoveryMetadata("Music Assistant", "/custom-sendspin"),
            sendspinDiscoveryMetadata(
                "fallback",
                mapOf(
                    "name" to "Music Assistant".toByteArray(),
                    "path" to "/custom-sendspin".toByteArray(),
                ),
            ),
        )
        assertEquals(null, sendspinDiscoveryMetadata("client", mapOf("name" to byteArrayOf())))
        assertEquals(
            "ws://192.168.1.20:8927/custom-sendspin",
            sendspinWebSocketUrl(InetAddress.getByName("192.168.1.20"), 8927, "/custom-sendspin"),
        )
    }

    private fun server(id: String, url: String) = DiscoveredServer(id, id, url)
}
