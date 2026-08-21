package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageMediaFormatTest {
    @Test
    fun detectsGifForExtensionlessAnimatedImageUrls() {
        assertEquals(
            ImageMediaFormat.GIF,
            detectImageMediaFormat("GIF89a".toByteArray(Charsets.US_ASCII)),
        )
    }

    @Test
    fun detectsWebpRegardlessOfUrlExtension() {
        val header = "RIFF".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 0, 0, 0) +
            "WEBPVP8X".toByteArray(Charsets.US_ASCII)

        assertEquals(ImageMediaFormat.WEBP, detectImageMediaFormat(header))
    }

    @Test
    fun rejectsNonImageResponseBodies() {
        assertNull(detectImageMediaFormat("<html>not an image".toByteArray(Charsets.US_ASCII)))
    }
}
