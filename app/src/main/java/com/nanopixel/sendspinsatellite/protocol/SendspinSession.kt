package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.nanopixel.sendspinsatellite.connection.PlayerAudioState
import com.nanopixel.sendspinsatellite.playback.ArtworkSnapshot
import com.nanopixel.sendspinsatellite.playback.NowPlayingSnapshot
import com.nanopixel.sendspinsatellite.playback.PlaybackCommand
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
        fun onControllerCommands(commands: Set<PlaybackCommand>)
        fun onPairingUi(snapshot: NativePlaybackEngine.PairingUiSnapshot)
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
    private val logProgressChanges =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var lastState: NativePlaybackEngine.State? = null
    private var lastNowPlayingRevision = -1L
    private var lastArtworkRevision = -1L
    private var lastControllerCapabilitiesRevision = -1L
    private var lastPairingUiRevision = -1L
    private var lastLoggedProgressPosition = Long.MIN_VALUE
    private var lastLoggedProgressDuration = Long.MIN_VALUE
    private var lastLoggedPlaybackSpeed = Int.MIN_VALUE
    private var lastLoggedGroupState: NowPlayingSnapshot.PlaybackState? = null

    init {
        poller.scheduleAtFixedRate(::publishFastState, 0, 250, TimeUnit.MILLISECONDS)
        poller.scheduleAtFixedRate(::publishDiagnostics, 0, 1, TimeUnit.SECONDS)
    }

    fun connect(address: String) {
        if (!engine.connect(address)) {
            listener.onState(SessionState.ERROR)
            val message = if (engine.diagnostics()?.secureStorageError == true) {
                "Secure Sendspin identity storage is unavailable. Check device storage and reconnect."
            } else {
                "Unable to start native audio output."
            }
            listener.onDiagnostics(Diagnostics(message = message))
        }
    }

    fun setNetworkAvailable(available: Boolean) = engine.setNetworkAvailable(available)

    fun requestOutputRecovery() = engine.requestOutputRecovery()

    fun suspendForFocus() = engine.suspendForFocus()

    fun resumeFromFocus() = engine.resumeFromFocus()

    fun requestControllerCommand(command: PlaybackCommand): Boolean =
        engine.requestControllerCommand(command)

    fun confirmPairingWindow() = engine.confirmPairingWindow()

    fun cancelPairingWindow() = engine.cancelPairingWindow()

    fun close() {
        engine.disconnect()
        listener.onControllerCommands(emptySet())
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
        publishControllerCapabilities()
        publishPairingUi()
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
        listener.onDiagnostics(
            Diagnostics(
                message = when {
                    snapshot.secureStorageError ->
                        "Secure Sendspin identity storage is unavailable. Check device storage and reconnect."
                    snapshot.lastFailure == FAILURE_HANDSHAKE_TIMEOUT ->
                        "The encrypted Sendspin connection could not be established. Check server compatibility, pairing, and network."
                    else -> null
                },
                nativeSnapshot = snapshot,
            ),
        )
    }

    private fun publishNowPlaying() {
        val snapshot = engine.nowPlayingIfChanged(lastNowPlayingRevision) ?: return
        lastNowPlayingRevision = snapshot.revision
        if (logProgressChanges) {
            val progress = snapshot.progress
            val reportedPosition = progress?.reportedPositionMs
            val duration = progress?.durationMs
            val playbackSpeed = progress?.playbackSpeedMilli
            val groupState = snapshot.group?.playbackState
            if (reportedPosition != lastLoggedProgressPosition ||
                duration != lastLoggedProgressDuration ||
                playbackSpeed != lastLoggedPlaybackSpeed ||
                groupState != lastLoggedGroupState
            ) {
                Log.d(
                    "SendspinProgress",
                    "group=$groupState reported=$reportedPosition " +
                        "current=${progress?.interpolatedPositionMs} " +
                        "duration=$duration speed=$playbackSpeed",
                )
                lastLoggedProgressPosition = reportedPosition ?: Long.MIN_VALUE
                lastLoggedProgressDuration = duration ?: Long.MIN_VALUE
                lastLoggedPlaybackSpeed = playbackSpeed ?: Int.MIN_VALUE
                lastLoggedGroupState = groupState
            }
        }
        listener.onNowPlaying(snapshot)
    }

    private fun publishArtwork() {
        val snapshot = engine.artworkIfChanged(lastArtworkRevision) ?: return
        lastArtworkRevision = snapshot.revision
        listener.onArtwork(snapshot)
    }

    private fun publishControllerCapabilities() {
        val capabilities = engine.controllerCapabilitiesIfChanged(lastControllerCapabilitiesRevision) ?: return
        lastControllerCapabilitiesRevision = capabilities.revision
        listener.onControllerCommands(capabilities.supportedCommands)
    }

    private fun publishPairingUi() {
        val snapshot = engine.pairingUiIfChanged(lastPairingUiRevision) ?: return
        lastPairingUiRevision = snapshot.revision
        listener.onPairingUi(snapshot)
    }

    private companion object {
        const val FAILURE_HANDSHAKE_TIMEOUT = 3
    }
}
