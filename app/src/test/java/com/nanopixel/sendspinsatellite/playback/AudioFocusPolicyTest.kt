package com.nanopixel.sendspinsatellite.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusPolicyTest {
    @Test
    fun `granted focus starts and transient loss resumes once`() {
        val policy = AudioFocusPolicy()

        assertEquals(AudioFocusPolicy.Action.REQUEST, policy.onConnect())
        assertEquals(AudioFocusPolicy.Action.START, policy.onFocusRequestResult(true))
        assertEquals(
            AudioFocusPolicy.Action.SUSPEND,
            policy.onFocusChange(AudioFocusPolicy.Change.LOSS_TRANSIENT),
        )
        assertEquals(AudioFocusPolicy.Action.NONE, policy.onFocusChange(AudioFocusPolicy.Change.LOSS_TRANSIENT))
        assertEquals(AudioFocusPolicy.Action.RESUME, policy.onFocusChange(AudioFocusPolicy.Change.GAIN))
        assertEquals(AudioFocusPolicy.Action.NONE, policy.onFocusChange(AudioFocusPolicy.Change.GAIN))
    }

    @Test
    fun `ducking follows the same clean suspend path`() {
        val policy = AudioFocusPolicy()
        policy.onConnect()
        policy.onFocusRequestResult(true)

        assertEquals(
            AudioFocusPolicy.Action.SUSPEND,
            policy.onFocusChange(AudioFocusPolicy.Change.LOSS_TRANSIENT_CAN_DUCK),
        )
        assertEquals(AudioFocusPolicy.Action.RESUME, policy.onFocusChange(AudioFocusPolicy.Change.GAIN))
    }

    @Test
    fun `denial and permanent loss cannot resume`() {
        val denied = AudioFocusPolicy()
        denied.onConnect()
        assertEquals(AudioFocusPolicy.Action.STOP, denied.onFocusRequestResult(false))
        assertEquals(AudioFocusPolicy.Action.NONE, denied.onFocusChange(AudioFocusPolicy.Change.GAIN))

        val lost = AudioFocusPolicy()
        lost.onConnect()
        lost.onFocusRequestResult(true)
        assertEquals(AudioFocusPolicy.Action.STOP, lost.onFocusChange(AudioFocusPolicy.Change.LOSS))
        assertEquals(AudioFocusPolicy.Action.NONE, lost.onFocusChange(AudioFocusPolicy.Change.GAIN))
    }

    @Test
    fun `user stop cancels a pending resume`() {
        val policy = AudioFocusPolicy()
        policy.onConnect()
        policy.onFocusRequestResult(true)
        policy.onFocusChange(AudioFocusPolicy.Change.LOSS_TRANSIENT)

        assertEquals(AudioFocusPolicy.Action.STOP, policy.onStop())
        assertEquals(AudioFocusPolicy.Action.NONE, policy.onFocusChange(AudioFocusPolicy.Change.GAIN))
    }
}