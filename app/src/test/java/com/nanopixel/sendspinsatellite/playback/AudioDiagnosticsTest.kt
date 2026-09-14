package com.nanopixel.sendspinsatellite.playback

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.protocol.NativePlaybackEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDiagnosticsTest {
    @Test
    fun `collector keeps only the newest bounded events`() {
        var now = 10L
        val collector = AudioDiagnosticsCollector(clockMs = { now++ }, maxEvents = 2)

        collector.recordEvent("first", "one")
        collector.recordEvent("second", "two")
        collector.recordEvent("third", "three")

        assertEquals(listOf("second", "third"), collector.snapshot().events.map { it.kind })
    }

    @Test
    fun `collector records positive native counter deltas and transitions`() {
        val collector = AudioDiagnosticsCollector(clockMs = { 1L })
        collector.updateNative(nativeSnapshot())
        collector.updateNative(
            nativeSnapshot(
                state = NativePlaybackEngine.State.PLAYING,
                underruns = 2,
                outputXruns = 1,
                outputRestarts = 1,
                hardResyncs = 1,
                reconnectCompletions = 1,
                outputSampleRate = 44_100,
            ),
        )

        val events = collector.snapshot().events
        assertTrue(events.any { it.kind == "underrun" })
        assertTrue(events.any { it.kind == "xrun" })
        assertTrue(events.any { it.kind == "output-restart" })
        assertTrue(events.any { it.kind == "hard-resync" })
        assertTrue(events.any { it.kind == "reconnect" })
        assertTrue(events.any { it.kind == "state" && it.message == "PLAYING" })
        assertTrue(events.any { it.kind == "output" && it.message.contains("44100Hz") })
    }

    @Test
    fun `report is line safe and does not include a server address`() {
        val diagnostics = AlphaAudioDiagnostics(
            nativeSnapshot = nativeSnapshot(),
            platform = AudioPlatformDiagnostics(
                manufacturer = "Acme\nAudio",
                brand = "Acme",
                model = "Speaker",
                device = "speaker",
                androidRelease = "14",
                apiLevel = 34,
                supportedAbis = listOf("arm64-v8a"),
                appVersion = "1.0",
                frameworkOutputSampleRateHz = 48_000,
                frameworkOutputFramesPerBuffer = 240,
                lowLatencyFeature = true,
                proAudioFeature = false,
                nativeOutputDeviceId = 4,
                outputs = emptyList(),
            ),
            events = listOf(AudioDiagnosticEvent(1L, "route", "changed\r\noutput")),
        )

        val report = AudioDiagnosticReportFormatter.format(diagnostics)

        assertTrue(report.contains("Acme Audio"))
        assertTrue(report.contains("changed output"))
        assertFalse(report.contains("Acme\nAudio"))
        assertFalse(report.contains("ws://"))
        assertFalse(report.contains("wss://"))
    }

    @Test
    fun `playback status carries diagnostics into the existing UI state`() {
        val diagnostics = AlphaAudioDiagnostics(events = listOf(AudioDiagnosticEvent(1L, "state", "READY")))

        val uiState = PlaybackStatus(
            connectionState = ConnectionState.READY,
            audioDiagnostics = diagnostics,
        ).toUiState(ConnectionUiState())

        assertEquals(diagnostics, uiState.audioDiagnostics)
    }

    private fun nativeSnapshot(
        state: NativePlaybackEngine.State = NativePlaybackEngine.State.READY,
        underruns: Long = 0,
        outputRestarts: Long = 0,
        hardResyncs: Long = 0,
        reconnectCompletions: Long = 0,
        outputXruns: Int = 0,
        outputSampleRate: Int = 48_000,
    ) = NativePlaybackEngine.Diagnostics(
        state = state,
        generation = 1,
        queuedFrames = 240,
        fifoCapacityFrames = 4_800,
        outputLatencyUs = 5_000,
        underruns = underruns,
        outputRestarts = outputRestarts,
        hardResyncs = hardResyncs,
        reconnectAttempts = reconnectCompletions,
        reconnectCompletions = reconnectCompletions,
        roundTripUs = 2_000,
        clockOffsetUs = 100,
        clockDriftPpm = 0,
        clockErrorUs = 20,
        clockSamples = 4,
        clockConverged = true,
        lastFailure = 0,
        outputStreamOpen = true,
        outputStreamState = 2,
        outputSampleRate = outputSampleRate,
        outputChannelCount = 2,
        outputFormat = 2,
        outputPerformanceMode = 12,
        outputSharingMode = 1,
        outputDeviceId = 4,
        outputSessionId = 9,
        outputFramesPerBurst = 240,
        outputBufferSizeFrames = 480,
        outputBufferCapacityFrames = 960,
        outputXruns = outputXruns,
    )
}