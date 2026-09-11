package com.nanopixel.sendspinsatellite.connection

/** The user-visible lifecycle of a Sendspin session. */
enum class ConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    HANDSHAKING("Handshaking"),
    SYNCHRONISING("Synchronising"),
    RECOVERING("Recovering"),
    READY("Synchronised"),
    ERROR("Error"),
}

data class ConnectionUiState(
    val serverAddress: String = "",
    val playerName: String = PlayerNamePolicy.defaultName,
    val playerNameError: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val detail: String = "Enter the address of your Sendspin server.",
    val serverName: String? = null,
    val roundTripUs: Long? = null,
    val clockOffsetUs: Long? = null,
    val clockSamples: Int = 0,
)
