package com.nanopixel.sendspinsatellite.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtworkDecoderTest {
    @Test
    fun malformedArtworkFallsBackToPlaceholder() = runBlocking {
        assertNull(decodeArtwork(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun validArtworkDecodesAndIsBounded() = runBlocking {
        val source = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        val encoded = ByteArrayOutputStream().also { output ->
            source.compress(Bitmap.CompressFormat.JPEG, 90, output)
            source.recycle()
        }.toByteArray()

        val decoded = decodeArtwork(encoded)

        assertNotNull(decoded)
        assertEquals(true, maxOf(decoded!!.width, decoded.height) <= 512)
        decoded.recycle()
    }
}
