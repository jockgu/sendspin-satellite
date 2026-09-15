package com.nanopixel.sendspinsatellite.playback

import androidx.annotation.Keep

@Keep
data class NowPlayingSnapshot(
    val revision: Long = 0,
    val generation: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val progress: Progress? = null,
    val group: Group? = null,
) {
    @Keep
    data class Progress(
        val reportedPositionMs: Long,
        val durationMs: Long,
        val playbackSpeedMilli: Int,
    )

    @Keep
    data class Group(
        val name: String? = null,
        val playbackState: PlaybackState? = null,
    )

    @Keep
    enum class PlaybackState { PLAYING, STOPPED }
}
