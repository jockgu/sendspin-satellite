package com.nanopixel.sendspinsatellite.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSynchronizerTest {
    @Test
    fun `converges on stable NTP style measurements`() {
        val synchronizer = ClockSynchronizer()

        repeat(8) { index ->
            val clientSent = 1_000_000L + index * 100_000L
            synchronizer.update(
                clientTransmittedUs = clientSent,
                serverReceivedUs = clientSent + 11_000L,
                serverTransmittedUs = clientSent + 11_100L,
                clientReceivedUs = clientSent + 2_100L,
            )
        }

        assertTrue(synchronizer.isConverged)
        assertEquals(10_000L, synchronizer.offsetUs)
        assertEquals(2_000L, synchronizer.roundTripUs)
    }

    @Test
    fun `rejects impossible negative round trips`() {
        val synchronizer = ClockSynchronizer()

        synchronizer.update(
            clientTransmittedUs = 100L,
            serverReceivedUs = 200L,
            serverTransmittedUs = 600L,
            clientReceivedUs = 300L,
        )

        assertEquals(0, synchronizer.sampleCount)
    }
}
