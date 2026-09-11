package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState

data class PlaybackStatus(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val detail: String = "Disconnected from Sendspin server.",
    val serverName: String? = null,
    val roundTripUs: Long? = null,
    val clockOffsetUs: Long? = null,
    val clockSamples: Int = 0,
)

fun PlaybackStatus.toUiState(current: ConnectionUiState) = current.copy(
    connectionState = connectionState,
    detail = detail,
    serverName = serverName,
    roundTripUs = roundTripUs,
    clockOffsetUs = clockOffsetUs,
    clockSamples = clockSamples,
)
