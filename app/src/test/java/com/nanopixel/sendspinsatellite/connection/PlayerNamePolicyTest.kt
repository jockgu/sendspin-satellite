package com.nanopixel.sendspinsatellite.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNamePolicyTest {
    @Test
    fun `default name includes the device model`() {
        assertEquals(
            "Sendspin Satellite Pixel 6",
            PlayerNamePolicy.defaultFor(" Pixel   6 "),
        )
    }

    @Test
    fun `blank device model uses the product name`() {
        assertEquals(PlayerNamePolicy.defaultName, PlayerNamePolicy.defaultFor("  "))
    }

    @Test
    fun `default name is capped at the code point limit`() {
        val name = PlayerNamePolicy.defaultFor("x".repeat(100))

        assertEquals(PlayerNamePolicy.maxCodePoints, name.codePointCount(0, name.length))
    }

    @Test
    fun `valid name is trimmed`() {
        val validation = PlayerNamePolicy.validate("  Kitchen Speaker  ")

        assertTrue(validation.isValid)
        assertEquals("Kitchen Speaker", validation.normalized)
        assertNull(validation.error)
    }

    @Test
    fun `blank name is rejected`() {
        val validation = PlayerNamePolicy.validate("  ")

        assertFalse(validation.isValid)
        assertNull(validation.normalized)
    }

    @Test
    fun `control characters are rejected`() {
        val validation = PlayerNamePolicy.validate("Kitchen\nSpeaker")

        assertFalse(validation.isValid)
        assertEquals("Player name contains unsupported control characters.", validation.error)
    }

    @Test
    fun `names longer than the limit are rejected by code point count`() {
        val emoji = String(Character.toChars(0x1F642))
        val validation = PlayerNamePolicy.validate(emoji.repeat(PlayerNamePolicy.maxCodePoints + 1))

        assertFalse(validation.isValid)
        assertNull(validation.normalized)
    }
}