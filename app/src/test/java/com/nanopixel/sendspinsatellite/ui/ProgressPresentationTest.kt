package com.nanopixel.sendspinsatellite.ui

import com.nanopixel.sendspinsatellite.connection.ConnectionState
import com.nanopixel.sendspinsatellite.connection.ConnectionUiState
import com.nanopixel.sendspinsatellite.playback.NowPlayingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressPresentationTest {
    @Test
    fun `duration formatting uses minutes and hours without rounding up`() {
        assertEquals("0:00", formatDurationMs(0))
        assertEquals("1:05", formatDurationMs(65_999))
        assertEquals("1:01:05", formatDurationMs(3_665_999))
    }

    @Test
    fun `progress clamps position and fraction to the finite duration`() {
        val beforeStart = progress(-1)
        assertEquals(0f, beforeStart.fraction)
        assertEquals("0:00", beforeStart.elapsedLabel)

        val afterEnd = progress(200_000)
        assertEquals(1f, afterEnd.fraction)
        assertEquals("3:00", afterEnd.elapsedLabel)
    }

    @Test
    fun `zero duration omits progress`() {
        assertNull(
            NowPlayingSnapshot.Progress(
                reportedPositionMs = 10_000,
                durationMs = 0,
                playbackSpeedMilli = 1_000,
            ).toPresentation(),
        )
    }

    @Test
    fun `paused progress stays at its native current position`() {
        val paused = NowPlayingSnapshot.Progress(
            reportedPositionMs = 12_000,
            durationMs = 180_000,
            playbackSpeedMilli = 0,
        ).toPresentation() ?: error("finite progress expected")

        assertEquals(12_000f / 180_000f, paused.fraction, 0.00001f)
        assertEquals("0:12", paused.elapsedLabel)
    }

    @Test
    fun `seek correction uses the new interpolated position directly`() {
        val beforeSeek = progress(60_000)
        val afterSeek = progress(10_000)

        assertEquals("1:00", beforeSeek.elapsedLabel)
        assertEquals("0:10", afterSeek.elapsedLabel)
    }

    @Test
    fun `track replacement uses the new duration and position`() {
        val presentation = ConnectionUiState(
            connectionState = ConnectionState.PLAYING,
            nowPlaying = NowPlayingSnapshot(
                title = "New track",
                progress = NowPlayingSnapshot.Progress(
                    reportedPositionMs = 2_000,
                    durationMs = 90_000,
                    playbackSpeedMilli = 1_000,
                    interpolatedPositionMs = 3_000,
                ),
            ),
        ).toNowPlayingPresentation()

        assertEquals("0:03", presentation.progress?.elapsedLabel)
        assertEquals("1:30", presentation.progress?.durationLabel)
    }

    @Test
    fun `progress is hidden outside active playback states`() {
        val progress = NowPlayingSnapshot.Progress(
            reportedPositionMs = 10_000,
            durationMs = 180_000,
            playbackSpeedMilli = 1_000,
        )
        listOf(
            ConnectionState.CONNECTING,
            ConnectionState.RECOVERING,
            ConnectionState.DISCONNECTED,
            ConnectionState.ERROR,
        ).forEach { state ->
            assertNull(
                ConnectionUiState(
                    connectionState = state,
                    nowPlaying = NowPlayingSnapshot(progress = progress),
                ).toNowPlayingPresentation().progress,
            )
        }
    }

    private fun progress(positionMs: Long): ProgressPresentation {
        return NowPlayingSnapshot.Progress(
            reportedPositionMs = positionMs,
            durationMs = 180_000,
            playbackSpeedMilli = 1_000,
            interpolatedPositionMs = positionMs,
        ).toPresentation() ?: error("finite progress expected")
    }
}
