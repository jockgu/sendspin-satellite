package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SendspinSession(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onState(state: SessionState)
        fun onDiagnostics(diagnostics: Diagnostics)
    }

    enum class SessionState { CONNECTING, HANDSHAKING, SYNCHRONISING, SYNCHRONISED, RECOVERING, DISCONNECTED, ERROR }

    data class Diagnostics(
        val serverName: String? = null,
        val roundTripUs: Long = 0,
        val offsetUs: Long = 0,
        val samples: Int = 0,
        val message: String? = null,
    )

    private val engine = NativePlaybackEngine(context)
    private val poller = Executors.newSingleThreadScheduledExecutor()
    private var lastState: NativePlaybackEngine.State? = null

    init {
        poller.scheduleAtFixedRate(::publishState, 0, 250, TimeUnit.MILLISECONDS)
    }

    fun connect(address: String) {
        if (!engine.connect(address)) {
            listener.onState(SessionState.ERROR)
            listener.onDiagnostics(Diagnostics(message = "Unable to start native audio output."))
        }
    }

    fun close() {
        engine.disconnect()
        listener.onState(SessionState.DISCONNECTED)
    }

    fun shutdown() {
        poller.shutdownNow()
        engine.close()
    }

    private fun publishState() {
        val state = engine.state()
        if (state == lastState) return
        lastState = state
        when (state) {
            NativePlaybackEngine.State.STOPPED -> listener.onState(SessionState.DISCONNECTED)
            NativePlaybackEngine.State.CONNECTING -> listener.onState(SessionState.CONNECTING)
            NativePlaybackEngine.State.SYNCHRONISING -> listener.onState(SessionState.SYNCHRONISING)
            NativePlaybackEngine.State.READY,
            NativePlaybackEngine.State.BUFFERING,
            NativePlaybackEngine.State.PLAYING -> listener.onState(SessionState.SYNCHRONISED)
            NativePlaybackEngine.State.RECOVERING -> listener.onState(SessionState.RECOVERING)
            NativePlaybackEngine.State.ERROR -> {
                listener.onState(SessionState.ERROR)
                listener.onDiagnostics(Diagnostics(message = "Native Sendspin connection failed."))
            }
        }
    }
}
