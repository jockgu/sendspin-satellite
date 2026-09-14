package com.nanopixel.sendspinsatellite.playback

/** A resolved Sendspin service found on the current local network. */
data class DiscoveredServer(
    val id: String,
    val name: String,
    val url: String,
    val addresses: List<String> = emptyList(),
)
