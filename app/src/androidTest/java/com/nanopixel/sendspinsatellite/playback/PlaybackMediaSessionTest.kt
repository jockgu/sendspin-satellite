package com.nanopixel.sendspinsatellite.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nanopixel.sendspinsatellite.connection.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class PlaybackMediaSessionTest {
    @Test
    fun publishesMetadataAndRoutesAdvertisedTransportCommands() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val commands = Collections.synchronizedList(mutableListOf<PlaybackCommand>())
        var bridge: PlaybackMediaSession? = null
        lateinit var controller: MediaController
        val playing = MediaSessionPresentation(
            connectionState = ConnectionState.PLAYING,
            active = true,
            state = MediaSessionState.PLAYING,
            actions = setOf(PlaybackCommand.PAUSE, PlaybackCommand.STOP),
            title = "Track",
            artist = "Artist",
            album = "Album",
            positionMs = 12_000,
            durationMs = 180_000,
            playbackSpeed = 1f,
            artworkGeneration = 1,
            artworkRevision = 0,
        )

        try {
            instrumentation.runOnMainSync {
                bridge = PlaybackMediaSession(
                    context = instrumentation.targetContext,
                    mainHandler = Handler(Looper.getMainLooper()),
                    onCommand = commands::add,
                    onArtworkUpdated = {},
                ).also { mediaSession ->
                    mediaSession.update(playing, ArtworkSnapshot())
                    bridge = mediaSession
                    controller = MediaController(instrumentation.targetContext, mediaSession.token)
                }
            }

            val playbackState = requireNotNull(controller.playbackState)
            val metadata = requireNotNull(controller.metadata)
            assertEquals(PlaybackState.STATE_PLAYING, playbackState.state)
            assertEquals(
                PlaybackState.ACTION_PAUSE,
                playbackState.actions and PlaybackState.ACTION_PAUSE,
            )
            assertEquals(
                PlaybackState.ACTION_STOP,
                playbackState.actions and PlaybackState.ACTION_STOP,
            )
            assertEquals("Track", metadata.getString(MediaMetadata.METADATA_KEY_TITLE))
            assertEquals("Artist", metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
            assertEquals("Album", metadata.getString(MediaMetadata.METADATA_KEY_ALBUM))

            controller.transportControls.pause()
            instrumentation.waitForIdleSync()
            controller.transportControls.stop()
            instrumentation.waitForIdleSync()

            instrumentation.runOnMainSync {
                bridge!!.update(
                    playing.copy(
                        state = MediaSessionState.PAUSED,
                        actions = setOf(PlaybackCommand.PLAY, PlaybackCommand.STOP),
                        playbackSpeed = 0f,
                    ),
                    ArtworkSnapshot(),
                )
            }
            controller.transportControls.play()
            instrumentation.waitForIdleSync()

            assertEquals(
                listOf(PlaybackCommand.PAUSE, PlaybackCommand.STOP, PlaybackCommand.PLAY),
                commands,
            )
            assertTrue(requireNotNull(controller.playbackState).actions and PlaybackState.ACTION_PLAY != 0L)
        } finally {
            bridge?.let { mediaSession ->
                instrumentation.runOnMainSync(mediaSession::release)
            }
        }
    }
}