package com.nanopixel.sendspinsatellite.protocol

class NativeClockEngine : AutoCloseable {
    private val lock = Any()
    private var handle = nativeCreate()

    fun reset() = synchronized(lock) {
        requireOpen()
        nativeReset(handle)
    }

    fun update(
        clientTransmittedUs: Long,
        serverReceivedUs: Long,
        serverTransmittedUs: Long,
        clientReceivedUs: Long,
    ): Diagnostics = synchronized(lock) {
        requireOpen()
        val values = nativeUpdate(handle, clientTransmittedUs, serverReceivedUs, serverTransmittedUs, clientReceivedUs)
        Diagnostics(values[0], values[1], values[2].toInt(), values[3] != 0L)
    }

    override fun close() = synchronized(lock) {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0
        }
    }

    private fun requireOpen() = check(handle != 0L) { "Native clock engine is closed" }

    data class Diagnostics(
        val roundTripUs: Long,
        val offsetUs: Long,
        val sampleCount: Int,
        val isConverged: Boolean,
    )

    private companion object {
        init {
            System.loadLibrary("sendspin_native")
        }

        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeReset(handle: Long)
        @JvmStatic private external fun nativeUpdate(
            handle: Long,
            clientTransmittedUs: Long,
            serverReceivedUs: Long,
            serverTransmittedUs: Long,
            clientReceivedUs: Long,
        ): LongArray
    }
}
