package com.nanopixel.sendspinsatellite.playback

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal sealed interface ServerDiscoveryDecision {
    data object None : ServerDiscoveryDecision
    data class AutoConnect(val server: DiscoveredServer) : ServerDiscoveryDecision
    data class Select(val servers: List<DiscoveredServer>) : ServerDiscoveryDecision
}

internal data class SendspinDiscoveryMetadata(
    val name: String,
    val path: String,
)

internal fun decideServerDiscovery(servers: Collection<DiscoveredServer>): ServerDiscoveryDecision {
    val unique = servers.distinctBy { it.id }
    return when (unique.size) {
        0 -> ServerDiscoveryDecision.None
        1 -> ServerDiscoveryDecision.AutoConnect(unique.single())
        else -> ServerDiscoveryDecision.Select(unique)
    }
}

internal fun preferredSendspinHost(addresses: List<InetAddress>): InetAddress? =
    addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()

internal fun sendspinDiscoveryMetadata(
    serviceName: String,
    attributes: Map<String, ByteArray>,
): SendspinDiscoveryMetadata? {
    fun attribute(name: String): String? = attributes.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.toString(Charsets.UTF_8)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val path = attribute("path")?.takeIf { it.startsWith('/') } ?: return null
    return SendspinDiscoveryMetadata(attribute("name") ?: serviceName, path)
}

internal fun sendspinWebSocketUrl(
    host: InetAddress?,
    port: Int,
    path: String = "/sendspin",
): String? {
    if (host == null || port !in 1..65535) return null
    val address = host.hostAddress?.trim().orEmpty()
    if (address.isEmpty() || !path.startsWith('/')) return null
    val formattedAddress = if (host is Inet6Address || address.contains(':')) {
        "[$address]"
    } else {
        address
    }
    return "ws://$formattedAddress:$port$path"
}
