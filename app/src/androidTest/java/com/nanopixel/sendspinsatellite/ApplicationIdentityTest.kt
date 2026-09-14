package com.nanopixel.sendspinsatellite

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationIdentityTest {
    @Test
    fun applicationContextUsesTheExpectedPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.nanopixel.sendspinsatellite", appContext.packageName)
    }
}
