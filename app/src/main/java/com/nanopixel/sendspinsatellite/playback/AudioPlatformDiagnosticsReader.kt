package com.nanopixel.sendspinsatellite.playback

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

object AudioPlatformDiagnosticsReader {
    fun read(
        context: Context,
        audioManager: AudioManager,
        nativeOutputDeviceId: Int? = null,
    ): AudioPlatformDiagnostics {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map(::toOutput)
        return AudioPlatformDiagnostics(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            appVersion = packageInfo.versionName.orEmpty(),
            frameworkOutputSampleRateHz = audioManager.propertyInt(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),
            frameworkOutputFramesPerBuffer = audioManager.propertyInt(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER),
            lowLatencyFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY),
            proAudioFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO),
            nativeOutputDeviceId = nativeOutputDeviceId?.takeIf { it >= 0 },
            outputs = outputs,
        )
    }

    private fun toOutput(device: AudioDeviceInfo): AudioOutputDeviceDiagnostics =
        AudioOutputDeviceDiagnostics(
            id = device.id,
            type = device.type,
            productName = device.productName?.toString().orEmpty().ifBlank { "Unknown" },
            sampleRates = device.sampleRates.toList(),
            channelMasks = device.channelMasks.toList(),
            channelCounts = device.channelCounts.toList(),
            encodings = device.encodings.toList(),
        )

    private fun AudioManager.propertyInt(name: String): Int? =
        getProperty(name)?.toIntOrNull()?.takeIf { it > 0 }
}