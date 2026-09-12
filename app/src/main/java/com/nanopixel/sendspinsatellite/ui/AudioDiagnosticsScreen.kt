package com.nanopixel.sendspinsatellite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nanopixel.sendspinsatellite.playback.AlphaAudioDiagnostics
import com.nanopixel.sendspinsatellite.playback.AudioDiagnosticReportFormatter
import com.nanopixel.sendspinsatellite.playback.AudioOutputDeviceDiagnostics
import com.nanopixel.sendspinsatellite.playback.AudioPlatformDiagnostics
import com.nanopixel.sendspinsatellite.playback.diagnosticLongValue
import com.nanopixel.sendspinsatellite.playback.diagnosticValue
import com.nanopixel.sendspinsatellite.playback.nativeFailureName
import com.nanopixel.sendspinsatellite.playback.oboeFormatName
import com.nanopixel.sendspinsatellite.playback.oboePerformanceModeName
import com.nanopixel.sendspinsatellite.playback.oboeSharingModeName
import com.nanopixel.sendspinsatellite.protocol.NativePlaybackEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDiagnosticsScreen(
    diagnostics: AlphaAudioDiagnostics?,
    onBack: () -> Unit,
) {
    val current = diagnostics ?: AlphaAudioDiagnostics()
    val report = remember(current) { AudioDiagnosticReportFormatter.format(current) }
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(report))
                            copied = true
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy report")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (copied) {
                Text("Report copied", style = MaterialTheme.typography.labelMedium)
            }
            NativeOutputSection(current.nativeSnapshot)
            PlatformSection(current.platform)
            EventsSection(current)
        }
    }
}

@Composable
private fun NativeOutputSection(snapshot: NativePlaybackEngine.Diagnostics?) {
    DiagnosticsSection("Actual negotiated Oboe output") {
        if (snapshot == null) {
            Text("Unavailable until the native playback engine reports a snapshot.")
        } else {
            DiagnosticRow("Engine state", snapshot.state.name)
            DiagnosticRow("Stream", "${snapshot.outputStreamOpen} / ${diagnosticValue(snapshot.outputStreamState)}")
            DiagnosticRow("Rate / channels", "${diagnosticValue(snapshot.outputSampleRate)} Hz / ${diagnosticValue(snapshot.outputChannelCount)}")
            DiagnosticRow(
                "Format / performance",
                "${oboeFormatName(snapshot.outputFormat)} / ${oboePerformanceModeName(snapshot.outputPerformanceMode)}",
            )
            DiagnosticRow("Sharing mode", oboeSharingModeName(snapshot.outputSharingMode))
            DiagnosticRow("Device / session", "${diagnosticValue(snapshot.outputDeviceId)} / ${diagnosticValue(snapshot.outputSessionId)}")
            DiagnosticRow(
                "Burst / buffer",
                "${diagnosticValue(snapshot.outputFramesPerBurst)} / ${diagnosticValue(snapshot.outputBufferSizeFrames)} / ${diagnosticValue(snapshot.outputBufferCapacityFrames)} frames",
            )
            DiagnosticRow("Latency", "${diagnosticLongValue(snapshot.outputLatencyUs)} us")
            DiagnosticRow("Application underruns", snapshot.underruns.toString())
            DiagnosticRow("Oboe x-runs", diagnosticValue(snapshot.outputXruns))
            DiagnosticRow("Output restarts", snapshot.outputRestarts.toString())
            DiagnosticRow("Hard resyncs", snapshot.hardResyncs.toString())
            DiagnosticRow("Reconnects", "${snapshot.reconnectCompletions} / ${snapshot.reconnectAttempts}")
            DiagnosticRow("Last failure", nativeFailureName(snapshot.lastFailure))
        }
    }
}

@Composable
private fun PlatformSection(platform: AudioPlatformDiagnostics?) {
    DiagnosticsSection("Android-visible device and route information") {
        if (platform == null) {
            Text("Unavailable until playback is connected.")
        } else {
            DiagnosticRow("Device", listOf(platform.manufacturer, platform.brand, platform.model).filter { it.isNotBlank() }.joinToString(" "))
            DiagnosticRow("Android / API", "${platform.androidRelease} / ${platform.apiLevel}")
            DiagnosticRow("Build device", platform.device)
            DiagnosticRow("App version", platform.appVersion)
            DiagnosticRow("ABIs", platform.supportedAbis.joinToString(", ").ifBlank { "Unavailable" })
            DiagnosticRow("Framework rate", platform.frameworkOutputSampleRateHz?.let { "$it Hz" } ?: "Unavailable")
            DiagnosticRow("Framework buffer", platform.frameworkOutputFramesPerBuffer?.let { "$it frames" } ?: "Unavailable")
            DiagnosticRow("Audio features", buildString {
                append(if (platform.lowLatencyFeature) "low-latency" else "no low-latency")
                append(" / ")
                append(if (platform.proAudioFeature) "pro-audio" else "no pro-audio")
            })
            DiagnosticRow("Native output device", platform.nativeOutputDeviceId?.toString() ?: "Unavailable")
            if (platform.outputs.isEmpty()) {
                DiagnosticRow("Output devices", "Unavailable")
            } else {
                Spacer(Modifier.size(4.dp))
                Text("Output devices", style = MaterialTheme.typography.titleSmall)
                platform.outputs.forEachIndexed { index, output ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    OutputDeviceRows(output)
                }
            }
        }
    }
}

@Composable
private fun OutputDeviceRows(output: AudioOutputDeviceDiagnostics) {
    DiagnosticRow("Device ${output.id}", "${output.productName} / type ${output.type}")
    DiagnosticRow("Sample rates", output.sampleRates.formatList())
    DiagnosticRow("Channel masks", output.channelMasks.formatList())
    DiagnosticRow("Channel counts", output.channelCounts.formatList())
    DiagnosticRow("Encodings", output.encodings.formatList())
}

@Composable
private fun EventsSection(diagnostics: AlphaAudioDiagnostics) {
    DiagnosticsSection("Recent events (${diagnostics.events.size})") {
        if (diagnostics.events.isEmpty()) {
            Text("No events recorded yet.")
        } else {
            diagnostics.events.asReversed().forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${event.kind} · ${event.elapsedRealtimeMs} ms",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(event.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            value.ifBlank { "Unavailable" },
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

private fun List<Int>.formatList(): String = joinToString(", ").ifBlank { "Unavailable" }