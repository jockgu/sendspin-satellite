package com.nanopixel.sendspinsatellite.protocol

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nanopixel.sendspinsatellite.connection.PlayerAudioState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePlayerVolumeTest {
    @Test
    fun initialVolumeAndMuteReachNativePlayerBeforeConnecting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NativePlaybackEngine(
            context,
            "Volume test",
            PlayerAudioState(volume = 37, muted = true),
        )

        try {
            val diagnostics = engine.diagnostics()
            assertEquals(37, diagnostics?.playerVolume)
            assertEquals(true, diagnostics?.playerMuted)
        } finally {
            engine.close()
        }
    }
}
