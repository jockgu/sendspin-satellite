package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import android.provider.Settings
import com.nanopixel.sendspinsatellite.connection.PlayerAudioState
import com.nanopixel.sendspinsatellite.playback.ArtworkSnapshot
import com.nanopixel.sendspinsatellite.playback.NowPlayingSnapshot

class NativePlaybackEngine(
    context: Context,
    playerName: String,
    initialPlayerAudioState: PlayerAudioState = PlayerAudioState(),
) : AutoCloseable {
    private var handle = nativeCreate(
        resolveClientId(context),
        playerName,
        initialPlayerAudioState.volume.coerceIn(0, 100),
        initialPlayerAudioState.muted,
    )

    fun connect(url: String): Boolean = nativeConnect(requireOpen(), url)
    fun disconnect() { if (handle != 0L) nativeDisconnect(handle) }
    fun setNetworkAvailable(available: Boolean) {
        if (handle != 0L) nativeSetNetworkAvailable(handle, available)
    }
    fun diagnostics(): Diagnostics? {
        if (handle == 0L) return null
        val values = nativeDiagnostics(requireOpen())
        if (values.size != DIAGNOSTICS_SIZE) return null
        val state = State.entries.getOrNull(values[0].toInt()) ?: State.ERROR
        return Diagnostics(
            state = state,
            generation = values[1],
            queuedFrames = values[2],
            fifoCapacityFrames = values[3],
            outputLatencyUs = values[4],
            underruns = values[5],
            outputRestarts = values[6],
            hardResyncs = values[7],
            reconnectAttempts = values[8],
            reconnectCompletions = values[9],
            roundTripUs = values[10],
            clockOffsetUs = values[11],
            clockDriftPpm = values[12],
            clockErrorUs = values[13],
            clockSamples = values[14],
            clockConverged = values[15] != 0L,
            lastFailure = values[16].toInt(),
            outputStreamOpen = values[17] != 0L,
            outputStreamState = values[18].toInt(),
            outputSampleRate = values[19].toInt(),
            outputChannelCount = values[20].toInt(),
            outputFormat = values[21].toInt(),
            outputPerformanceMode = values[22].toInt(),
            outputSharingMode = values[23].toInt(),
            outputDeviceId = values[24].toInt(),
            outputSessionId = values[25].toInt(),
            outputFramesPerBurst = values[26].toInt(),
            outputBufferSizeFrames = values[27].toInt(),
            outputBufferCapacityFrames = values[28].toInt(),
            outputXruns = values[29].toInt(),
            playerVolume = values[30].toInt(),
            playerMuted = values[31] != 0L,
        )
    }
    fun nowPlayingIfChanged(knownRevision: Long): NowPlayingSnapshot? {
        if (handle == 0L) return null
        return nativeNowPlayingIfChanged(requireOpen(), knownRevision)
    }
    fun artworkIfChanged(knownRevision: Long): ArtworkSnapshot? {
        if (handle == 0L) return null
        return nativeArtworkIfChanged(requireOpen(), knownRevision)
    }
    fun requestOutputRecovery() {
        if (handle != 0L) nativeRequestRecovery(handle, RECOVERY_CAUSE_ROUTE_CHANGE)
    }
    fun suspendForFocus() {
        if (handle != 0L) nativeSuspendForFocus(handle)
    }
    fun resumeFromFocus() {
        if (handle != 0L) nativeResumeFromFocus(handle)
    }
    fun state(): State = State.entries[nativeState(requireOpen())]

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0
        }
    }

    private fun requireOpen(): Long = check(handle != 0L) { "Native playback engine is closed" }.let { handle }

    enum class State { STOPPED, CONNECTING, SYNCHRONISING, READY, BUFFERING, PLAYING, RECOVERING, ERROR }

    data class Diagnostics(
        val state: State,
        val generation: Long,
        val queuedFrames: Long,
        val fifoCapacityFrames: Long,
        val outputLatencyUs: Long,
        val underruns: Long,
        val outputRestarts: Long,
        val hardResyncs: Long,
        val reconnectAttempts: Long,
        val reconnectCompletions: Long,
        val roundTripUs: Long,
        val clockOffsetUs: Long,
        val clockDriftPpm: Long,
        val clockErrorUs: Long,
        val clockSamples: Long,
        val clockConverged: Boolean,
        val lastFailure: Int,
        val outputStreamOpen: Boolean,
        val outputStreamState: Int,
        val outputSampleRate: Int,
        val outputChannelCount: Int,
        val outputFormat: Int,
        val outputPerformanceMode: Int,
        val outputSharingMode: Int,
        val outputDeviceId: Int,
        val outputSessionId: Int,
        val outputFramesPerBurst: Int,
        val outputBufferSizeFrames: Int,
        val outputBufferCapacityFrames: Int,
        val outputXruns: Int,
        val playerVolume: Int = 100,
        val playerMuted: Boolean = false,
    )

    private companion object {
        init { System.loadLibrary("sendspin_native") }
        private fun resolveClientId(context: Context): String {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            return if (androidId.isNullOrBlank()) "android-unknown-client" else "android-$androidId"
        }

        @JvmStatic private external fun nativeCreate(
            clientId: String,
            playerName: String,
            initialVolume: Int,
            initialMuted: Boolean,
        ): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeConnect(handle: Long, url: String): Boolean
        @JvmStatic private external fun nativeDisconnect(handle: Long)
        @JvmStatic private external fun nativeSetNetworkAvailable(handle: Long, available: Boolean)
        @JvmStatic private external fun nativeDiagnostics(handle: Long): LongArray
        @JvmStatic private external fun nativeNowPlayingIfChanged(
            handle: Long,
            knownRevision: Long,
        ): NowPlayingSnapshot?
        @JvmStatic private external fun nativeArtworkIfChanged(
            handle: Long,
            knownRevision: Long,
        ): ArtworkSnapshot?
        @JvmStatic private external fun nativeRequestRecovery(handle: Long, cause: Int)
        @JvmStatic private external fun nativeSuspendForFocus(handle: Long)
        @JvmStatic private external fun nativeResumeFromFocus(handle: Long)
        @JvmStatic private external fun nativeState(handle: Long): Int

        private const val RECOVERY_CAUSE_ROUTE_CHANGE = 1
        private const val DIAGNOSTICS_SIZE = 32
    }
}
