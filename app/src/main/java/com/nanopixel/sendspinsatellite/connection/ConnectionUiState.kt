package com.nanopixel.sendspinsatellite.connection

import com.nanopixel.sendspinsatellite.playback.AlphaAudioDiagnostics
import com.nanopixel.sendspinsatellite.playback.DiscoveredServer

/** The user-visible lifecycle of a Sendspin session. */
enum class ConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    DISCOVERING("Finding server"),
    CONNECTING("Connecting"),
    HANDSHAKING("Handshaking"),
    SYNCHRONISING("Synchronising"),
    RECOVERING("Recovering"),
    READY("Synchronised"),
    ERROR("Error"),
}

data class ConnectionUiState(
    val savedServer: SavedServer? = null,
    val serverAddress: String = "",
    val playerName: String = PlayerNamePolicy.defaultName,
    val playerNameError: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val detail: String = "Enter the address of your Sendspin server.",
    val serverName: String? = null,
    val roundTripUs: Long? = null,
    val clockOffsetUs: Long? = null,
    val clockSamples: Int = 0,
    val audioDiagnostics: AlphaAudioDiagnostics? = null,
    val discoveredServers: List<DiscoveredServer> = emptyList(),
)
