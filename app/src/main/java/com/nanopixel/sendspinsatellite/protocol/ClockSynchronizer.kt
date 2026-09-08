package com.nanopixel.sendspinsatellite.protocol

import kotlin.math.abs

/**
 * A bounded NTP-style estimator used only for Phase 1 connection readiness.
 *
 * The native time filter will replace this in Phase 2 before any playback is
 * enabled. Keeping this implementation separate ensures there is a single
 * owner for all clock measurements at every stage of the project.
 */
class ClockSynchronizer {
    private val samples = ArrayDeque<Sample>()

    val isConverged: Boolean
        get() = samples.size >= REQUIRED_SAMPLES && maxOffsetDeviationUs() <= MAX_OFFSET_DEVIATION_US

    val sampleCount: Int
        get() = samples.size

    val offsetUs: Long
        get() = if (samples.isEmpty()) 0 else samples.map { it.offsetUs }.sorted()[samples.size / 2]

    val roundTripUs: Long
        get() = if (samples.isEmpty()) 0 else samples.minOf { it.roundTripUs }

    fun reset() = samples.clear()

    fun update(
        clientTransmittedUs: Long,
        serverReceivedUs: Long,
        serverTransmittedUs: Long,
        clientReceivedUs: Long,
    ) {
        val roundTrip = (clientReceivedUs - clientTransmittedUs) -
            (serverTransmittedUs - serverReceivedUs)
        if (roundTrip < 0) return

        val offset = ((serverReceivedUs - clientTransmittedUs) +
            (serverTransmittedUs - clientReceivedUs)) / 2
        samples.addLast(Sample(offset, roundTrip))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    private fun maxOffsetDeviationUs(): Long {
        val center = offsetUs
        return samples.maxOf { abs(it.offsetUs - center) }
    }

    private data class Sample(val offsetUs: Long, val roundTripUs: Long)

    private companion object {
        const val REQUIRED_SAMPLES = 8
        const val MAX_SAMPLES = 16
        const val MAX_OFFSET_DEVIATION_US = 5_000L
    }
}
