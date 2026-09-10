package com.nanopixel.sendspinsatellite.protocol

import android.content.Context
import android.provider.Settings

class NativePlaybackEngine(
    context: Context,
) : AutoCloseable {
    private var handle = nativeCreate(resolveClientId(context))

    fun connect(url: String): Boolean = nativeConnect(requireOpen(), url)
    fun disconnect() { if (handle != 0L) nativeDisconnect(handle) }
    fun state(): State = State.entries[nativeState(requireOpen())]

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0
        }
    }

    private fun requireOpen(): Long = check(handle != 0L) { "Native playback engine is closed" }.let { handle }

    enum class State { STOPPED, CONNECTING, SYNCHRONISING, READY, BUFFERING, PLAYING, RECOVERING, ERROR }

    private companion object {
        init { System.loadLibrary("sendspin_native") }
        private fun resolveClientId(context: Context): String {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            return if (androidId.isNullOrBlank()) "android-unknown-client" else "android-$androidId"
        }

        @JvmStatic private external fun nativeCreate(clientId: String): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeConnect(handle: Long, url: String): Boolean
        @JvmStatic private external fun nativeDisconnect(handle: Long)
        @JvmStatic private external fun nativeState(handle: Long): Int
    }
}
