package com.jockgu.sendspinsatellite.connection

/** The user-visible lifecycle of a Sendspin session. */
enum class ConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    HANDSHAKING("Handshaking"),
    SYNCHRONISING("Synchronising"),
    READY("Ready"),
    ERROR("Error"),
}

data class ConnectionUiState(
    val serverAddress: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val detail: String = "Enter the address of your Sendspin server.",
)
