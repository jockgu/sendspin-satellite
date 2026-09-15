package com.nanopixel.sendspinsatellite.connection

internal fun shouldAutoConnect(
    lastWorkingServer: String?,
    automaticConnectionAttempted: Boolean,
    state: ConnectionState,
): Boolean = !automaticConnectionAttempted &&
    !lastWorkingServer.isNullOrBlank() &&
    state in setOf(ConnectionState.DISCONNECTED, ConnectionState.ERROR)
