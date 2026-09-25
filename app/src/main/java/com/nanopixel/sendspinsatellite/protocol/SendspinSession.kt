package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import com.nanopixel.sendspinsatellite.connection.PlayerAudioState
import com.nanopixel.sendspinsatellite.playback.ArtworkSnapshot
import com.nanopixel.sendspinsatellite.playback.NowPlayingSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SendspinSession(
    context: Context,
    playerName: String,
    initialPlayerAudioState: PlayerAudioState = PlayerAudioState(),
    private val listener: Listener,
) {
    interface Listener {
        fun onState(state: SessionState)
        fun onDiagnostics(diagnostics: Diagnostics)
        fun onNowPlaying(snapshot: NowPlayingSnapshot)
        fun onArtwork(snapshot: ArtworkSnapshot)
    }

    enum class SessionState {
        CONNECTING,
        HANDSHAKING,
        SYNCHRONISING,
        SYNCHRONISED,
        BUFFERING,
        PLAYING,
        RECOVERING,
        DISCONNECTED,
        ERROR,
    }

    data class Diagnostics(
        val serverName: String? = null,
        val roundTripUs: Long = 0,
        val offsetUs: Long = 0,
        val samples: Int = 0,
        val message: String? = null,
        val nativeSnapshot: NativePlaybackEngine.Diagnostics? = null,
    )

    private val engine = NativePlaybackEngine(context, playerName, initialPlayerAudioState)
    private val poller = Executors.newSingleThreadScheduledExecutor()
    private var lastState: NativePlaybackEngine.State? = null
    private var lastNowPlayingRevision = -1L
    private var lastArtworkRevision = -1L

    init {
        poller.scheduleAtFixedRate(::publishFastState, 0, 250, TimeUnit.MILLISECONDS)
        poller.scheduleAtFixedRate(::publishDiagnostics, 0, 1, TimeUnit.SECONDS)
    }

    fun connect(address: String) {
        if (!engine.connect(address)) {
            listener.onState(SessionState.ERROR)
            listener.onDiagnostics(Diagnostics(message = "Unable to start native audio output."))
        }
    }

    fun setNetworkAvailable(available: Boolean) = engine.setNetworkAvailable(available)

    fun requestOutputRecovery() = engine.requestOutputRecovery()

    fun suspendForFocus() = engine.suspendForFocus()

    fun resumeFromFocus() = engine.resumeFromFocus()

    fun close() {
        engine.disconnect()
        listener.onState(SessionState.DISCONNECTED)
    }

    fun shutdown() {
        poller.shutdownNow()
        engine.close()
    }

    private fun publishFastState() {
        publishState()
        publishNowPlaying()
        publishArtwork()
    }

    private fun publishState() {
        val state = engine.state()
        if (state == lastState) return
        lastState = state
        when (state) {
            NativePlaybackEngine.State.STOPPED -> listener.onState(SessionState.DISCONNECTED)
            NativePlaybackEngine.State.CONNECTING -> listener.onState(SessionState.CONNECTING)
            NativePlaybackEngine.State.SYNCHRONISING -> listener.onState(SessionState.SYNCHRONISING)
            NativePlaybackEngine.State.READY -> listener.onState(SessionState.SYNCHRONISED)
            NativePlaybackEngine.State.BUFFERING -> listener.onState(SessionState.BUFFERING)
            NativePlaybackEngine.State.PLAYING -> listener.onState(SessionState.PLAYING)
            NativePlaybackEngine.State.RECOVERING -> listener.onState(SessionState.RECOVERING)
            NativePlaybackEngine.State.ERROR -> {
                listener.onState(SessionState.ERROR)
                listener.onDiagnostics(Diagnostics(message = "Native Sendspin connection failed."))
            }
        }
    }

    private fun publishDiagnostics() {
        val snapshot = engine.diagnostics() ?: return
        if (snapshot.state == NativePlaybackEngine.State.STOPPED) return
        listener.onDiagnostics(Diagnostics(nativeSnapshot = snapshot))
    }

    private fun publishNowPlaying() {
        val snapshot = engine.nowPlayingIfChanged(lastNowPlayingRevision) ?: return
        lastNowPlayingRevision = snapshot.revision
        listener.onNowPlaying(snapshot)
    }

    private fun publishArtwork() {
        val snapshot = engine.artworkIfChanged(lastArtworkRevision) ?: return
        lastArtworkRevision = snapshot.revision
        listener.onArtwork(snapshot)
    }
}
