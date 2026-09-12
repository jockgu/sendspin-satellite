package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.protocol.NativePlaybackEngine
import java.util.ArrayDeque

data class AudioOutputDeviceDiagnostics(
    val id: Int,
    val type: Int,
    val productName: String,
    val sampleRates: List<Int>,
    val channelMasks: List<Int>,
    val channelCounts: List<Int>,
    val encodings: List<Int>,
)

data class AudioPlatformDiagnostics(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val apiLevel: Int,
    val supportedAbis: List<String>,
    val appVersion: String,
    val frameworkOutputSampleRateHz: Int?,
    val frameworkOutputFramesPerBuffer: Int?,
    val lowLatencyFeature: Boolean,
    val proAudioFeature: Boolean,
    val nativeOutputDeviceId: Int?,
    val outputs: List<AudioOutputDeviceDiagnostics>,
)

data class AudioDiagnosticEvent(
    val elapsedRealtimeMs: Long,
    val kind: String,
    val message: String,
)

data class AlphaAudioDiagnostics(
    val nativeSnapshot: NativePlaybackEngine.Diagnostics? = null,
    val platform: AudioPlatformDiagnostics? = null,
    val events: List<AudioDiagnosticEvent> = emptyList(),
)

class AudioDiagnosticsCollector(
    private val clockMs: () -> Long,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    private val eventLog = ArrayDeque<AudioDiagnosticEvent>()
    private var previousSnapshot: NativePlaybackEngine.Diagnostics? = null
    private var nativeSnapshot: NativePlaybackEngine.Diagnostics? = null
    private var platform: AudioPlatformDiagnostics? = null

    init {
        require(maxEvents > 0)
    }

    @Synchronized
    fun reset() {
        eventLog.clear()
        previousSnapshot = null
        nativeSnapshot = null
        platform = null
    }

    @Synchronized
    fun updatePlatform(value: AudioPlatformDiagnostics) {
        platform = value
    }

    @Synchronized
    fun recordEvent(kind: String, message: String) {
        appendEvent(kind, message)
    }

    @Synchronized
    fun updateNative(snapshot: NativePlaybackEngine.Diagnostics): AlphaAudioDiagnostics {
        val previous = previousSnapshot
        if (previous != null) {
            recordCounterDelta("underrun", previous.underruns, snapshot.underruns, snapshot)
            recordCounterDelta(
                "xrun",
                previous.outputXruns.toLong(),
                snapshot.outputXruns.toLong(),
                snapshot,
            )
            recordCounterDelta("output-restart", previous.outputRestarts, snapshot.outputRestarts, snapshot)
            recordCounterDelta("hard-resync", previous.hardResyncs, snapshot.hardResyncs, snapshot)
            recordCounterDelta(
                "reconnect",
                previous.reconnectCompletions,
                snapshot.reconnectCompletions,
                snapshot,
            )
            if (previous.state != snapshot.state) {
                appendEvent("state", snapshot.state.name)
            }
            if (previous.lastFailure != snapshot.lastFailure && snapshot.lastFailure != 0) {
                appendEvent("failure", nativeFailureName(snapshot.lastFailure))
            }
            if (previous.outputSampleRate != snapshot.outputSampleRate ||
                previous.outputDeviceId != snapshot.outputDeviceId ||
                previous.outputChannelCount != snapshot.outputChannelCount
            ) {
                appendEvent("output", outputSummary(snapshot))
            }
        } else {
            appendEvent("state", snapshot.state.name)
            appendEvent("output", outputSummary(snapshot))
        }
        previousSnapshot = snapshot
        nativeSnapshot = snapshot
        return snapshot()
    }

    @Synchronized
    fun snapshot(): AlphaAudioDiagnostics = AlphaAudioDiagnostics(
        nativeSnapshot = nativeSnapshot,
        platform = platform,
        events = eventLog.toList(),
    )

    private fun recordCounterDelta(
        kind: String,
        previous: Long,
        current: Long,
        snapshot: NativePlaybackEngine.Diagnostics,
    ) {
        if (previous < 0 || current <= previous) return
        appendEvent(
            kind,
            "+${current - previous}; buffer=${snapshot.queuedFrames}/${snapshot.fifoCapacityFrames}",
        )
    }

    private fun appendEvent(kind: String, message: String) {
        eventLog.addLast(AudioDiagnosticEvent(clockMs(), kind, message))
        while (eventLog.size > maxEvents) eventLog.removeFirst()
    }

    private fun outputSummary(snapshot: NativePlaybackEngine.Diagnostics): String {
        val rate = snapshot.outputSampleRate.takeIf { it > 0 }?.toString() ?: "unknown"
        val channels = snapshot.outputChannelCount.takeIf { it > 0 }?.toString() ?: "unknown"
        val device = snapshot.outputDeviceId.takeIf { it >= 0 }?.toString() ?: "unknown"
        return "${rate}Hz/${channels}ch device=$device"
    }

    companion object {
        const val DEFAULT_MAX_EVENTS = 100
    }
}

fun nativeFailureName(value: Int): String = when (value) {
    0 -> "None"
    1 -> "Network unavailable"
    2 -> "Transport lost"
    3 -> "Handshake timeout"
    4 -> "Output error"
    5 -> "Route change"
    6 -> "Focus resume"
    7 -> "Output restart failed"
    else -> "Unknown ($value)"
}

fun oboeFormatName(value: Int): String = when (value) {
    0 -> "Unspecified"
    1 -> "I8"
    2 -> "I16"
    3 -> "I24"
    4 -> "I32"
    5 -> "Float"
    6 -> "IEC61937"
    7 -> "MP3"
    8 -> "AAC"
    9 -> "Opus"
    10 -> "Vorbis"
    else -> "Code $value"
}

fun oboePerformanceModeName(value: Int): String = when (value) {
    10 -> "None"
    11 -> "Power saving"
    12 -> "Low latency"
    else -> "Code $value"
}

fun oboeSharingModeName(value: Int): String = when (value) {
    0 -> "Exclusive"
    1 -> "Shared"
    else -> "Code $value"
}

fun diagnosticValue(value: Int): String = if (value < 0) "Unavailable" else value.toString()

fun diagnosticLongValue(value: Long): String = if (value < 0) "Unavailable" else value.toString()

object AudioDiagnosticReportFormatter {
    fun format(diagnostics: AlphaAudioDiagnostics): String = buildString {
        appendLine("Sendspin Satellite alpha audio diagnostics")
        appendLine("Actual negotiated Oboe output")
        val native = diagnostics.nativeSnapshot
        if (native == null) {
            appendLine("  unavailable")
        } else {
            appendLine("  state=${native.state.name} generation=${native.generation}")
            appendLine("  streamOpen=${native.outputStreamOpen} streamState=${diagnosticValue(native.outputStreamState)}")
            appendLine("  sampleRateHz=${diagnosticValue(native.outputSampleRate)} channels=${diagnosticValue(native.outputChannelCount)}")
            appendLine("  format=${oboeFormatName(native.outputFormat)} performance=${oboePerformanceModeName(native.outputPerformanceMode)} sharing=${oboeSharingModeName(native.outputSharingMode)}")
            appendLine("  deviceId=${diagnosticValue(native.outputDeviceId)} sessionId=${diagnosticValue(native.outputSessionId)}")
            appendLine("  framesPerBurst=${diagnosticValue(native.outputFramesPerBurst)} bufferFrames=${diagnosticValue(native.outputBufferSizeFrames)}/${diagnosticValue(native.outputBufferCapacityFrames)}")
            appendLine("  latencyUs=${diagnosticLongValue(native.outputLatencyUs)} appUnderruns=${native.underruns} oboeXruns=${diagnosticValue(native.outputXruns)}")
            appendLine("  outputRestarts=${native.outputRestarts} hardResyncs=${native.hardResyncs} reconnects=${native.reconnectCompletions}/${native.reconnectAttempts}")
            appendLine("  lastFailure=${nativeFailureName(native.lastFailure)}")
        }
        appendLine("Android-visible device and route information")
        val platform = diagnostics.platform
        if (platform == null) {
            appendLine("  unavailable")
        } else {
            appendLine("  manufacturer=${clean(platform.manufacturer)} brand=${clean(platform.brand)} model=${clean(platform.model)} device=${clean(platform.device)}")
            appendLine("  android=${clean(platform.androidRelease)} api=${platform.apiLevel} app=${clean(platform.appVersion)}")
            appendLine("  abis=${platform.supportedAbis.joinToString(",")}")
            appendLine("  frameworkOutputSampleRateHz=${platform.frameworkOutputSampleRateHz ?: "Unavailable"} frameworkOutputFramesPerBuffer=${platform.frameworkOutputFramesPerBuffer ?: "Unavailable"}")
            appendLine("  lowLatencyFeature=${platform.lowLatencyFeature} proAudioFeature=${platform.proAudioFeature} nativeOutputDeviceId=${platform.nativeOutputDeviceId ?: "Unavailable"}")
            platform.outputs.forEach { output ->
                appendLine("  output id=${output.id} type=${output.type} product=${clean(output.productName)}")
                appendLine("    sampleRates=${output.sampleRates.joinToString(",").ifEmpty { "Unavailable" }} channelMasks=${output.channelMasks.joinToString(",").ifEmpty { "Unavailable" }} channelCounts=${output.channelCounts.joinToString(",").ifEmpty { "Unavailable" }} encodings=${output.encodings.joinToString(",").ifEmpty { "Unavailable" }}")
            }
        }
        appendLine("Recent events (${diagnostics.events.size})")
        diagnostics.events.forEach { event ->
            appendLine("  ${event.elapsedRealtimeMs} ${clean(event.kind)} ${clean(event.message)}")
        }
    }

    private fun clean(value: String): String = value
        .replace("\r\n", " ")
        .replace('\n', ' ')
        .replace('\r', ' ')
}