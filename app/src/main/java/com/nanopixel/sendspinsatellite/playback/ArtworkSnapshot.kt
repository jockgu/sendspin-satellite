package com.nanopixel.sendspinsatellite.playback

import androidx.annotation.Keep

@Keep
data class ArtworkSnapshot(
    val revision: Long = 0,
    val generation: Long = 0,
    val encodedJpeg: ByteArray? = null,
)
