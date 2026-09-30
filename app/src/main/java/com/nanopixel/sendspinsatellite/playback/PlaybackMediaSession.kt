package com.nanopixel.sendspinsatellite.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class PlaybackMediaSession(
    context: Context,
    private val mainHandler: Handler,
    private val onCommand: (PlaybackCommand) -> Unit,
    private val onArtworkUpdated: () -> Unit,
    private val decoder: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    private val session = MediaSession(context, SESSION_TAG)
    private var currentPresentation: MediaSessionPresentation? = null
    private var requestedArtwork: ArtworkKey? = null
    private var decodedArtwork: Bitmap? = null
    private var decodeGeneration = 0L
    private var released = false

    init {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = onCommand(PlaybackCommand.PLAY)

            override fun onPause() = onCommand(PlaybackCommand.PAUSE)

            override fun onStop() = onCommand(PlaybackCommand.STOP)
        }, mainHandler)
    }

    val token: MediaSession.Token
        get() = session.sessionToken

    fun update(presentation: MediaSessionPresentation, artwork: ArtworkSnapshot) {
        check(!released) { "MediaSession is released" }
        currentPresentation = presentation
        session.isActive = presentation.active
        session.setPlaybackState(presentation.toPlaybackState())
        updateArtwork(artwork)
        publishMetadata(presentation)
    }

    fun release() {
        if (released) return
        released = true
        decodeGeneration++
        decodedArtwork = null
        requestedArtwork = null
        decoder.shutdownNow()
        session.isActive = false
        session.release()
    }

    private fun updateArtwork(artwork: ArtworkSnapshot) {
        val bytes = artwork.encodedJpeg
        val key = bytes?.let { ArtworkKey(artwork.generation, artwork.revision) }
        if (key == requestedArtwork) return

        requestedArtwork = key
        decodedArtwork = null
        val taskGeneration = ++decodeGeneration
        if (bytes == null) return

        decoder.execute {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            mainHandler.post {
                if (released || taskGeneration != decodeGeneration || requestedArtwork != key) return@post
                decodedArtwork = bitmap
                currentPresentation?.let(::publishMetadata)
                onArtworkUpdated()
            }
        }
    }

    private fun publishMetadata(presentation: MediaSessionPresentation) {
        if (!presentation.active) {
            session.setMetadata(null)
            return
        }
        val metadata = MediaMetadata.Builder().apply {
            presentation.title?.let { putString(MediaMetadata.METADATA_KEY_TITLE, it) }
            presentation.artist?.let { putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
            presentation.album?.let { putString(MediaMetadata.METADATA_KEY_ALBUM, it) }
            presentation.durationMs?.let { putLong(MediaMetadata.METADATA_KEY_DURATION, it) }
            decodedArtwork?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        }.build()
        session.setMetadata(metadata)
    }

    private fun MediaSessionPresentation.toPlaybackState(): PlaybackState {
        val platformState = when (state) {
            MediaSessionState.NONE -> PlaybackState.STATE_NONE
            MediaSessionState.BUFFERING -> PlaybackState.STATE_BUFFERING
            MediaSessionState.PLAYING -> PlaybackState.STATE_PLAYING
            MediaSessionState.PAUSED -> PlaybackState.STATE_PAUSED
        }
        val actionMask = actions.fold(0L) { mask, command ->
            mask or when (command) {
                PlaybackCommand.PLAY -> PlaybackState.ACTION_PLAY
                PlaybackCommand.PAUSE -> PlaybackState.ACTION_PAUSE
                PlaybackCommand.STOP -> PlaybackState.ACTION_STOP
            }
        }
        return PlaybackState.Builder()
            .setActions(actionMask)
            .setState(platformState, positionMs, playbackSpeed, SystemClock.elapsedRealtime())
            .build()
    }

    private data class ArtworkKey(
        val generation: Long,
        val revision: Long,
    )

    private companion object {
        const val SESSION_TAG = "SendspinPlayback"
    }
}