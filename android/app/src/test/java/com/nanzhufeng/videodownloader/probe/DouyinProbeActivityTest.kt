package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinProbeActivityTest {
    @Test
    fun iesDouyinSharePageIsCanonicalizedToAuthenticatedVideoPage() {
        assertEquals(
            "https://www.douyin.com/video/7669248142533973995",
            DouyinProbeActivity.canonicalDouyinVideoUrl(
                "https://www.iesdouyin.com/share/video/7669248142533973995/?region=CN",
            ),
        )
    }

    @Test
    fun unrelatedHostCannotTriggerCanonicalRedirect() {
        assertNull(
            DouyinProbeActivity.canonicalDouyinVideoUrl(
                "https://www.iesdouyin.com.attacker.example/share/video/7669248142533973995/",
            ),
        )
    }

    @Test
    fun mobileBrowserUserAgentKeepsTheInstalledEngineVersion() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 16; PKH120 Build/V) " +
                "AppleWebKit/537.36 Chrome/150.0.0.0 Mobile Safari/537.36",
            DouyinProbeActivity.mobileBrowserUserAgent(
                "Mozilla/5.0 (Linux; Android 16; PKH120 Build/V; wv) " +
                    "AppleWebKit/537.36 Version/4.0 Chrome/150.0.0.0 Mobile Safari/537.36",
            ),
        )
    }

    @Test
    fun onlyCanonicalNotePathEnablesStrictGalleryMode() {
        assertTrue(
            DouyinProbeActivity.isDouyinNoteUrl(
                "https://www.douyin.com/note/7676041925425736777",
            ),
        )
        assertFalse(
            DouyinProbeActivity.isDouyinNoteUrl(
                "https://www.douyin.com/video/7676041925425736777",
            ),
        )
    }

    @Test
    fun videoElementNeverFallsBackToThePreviewImage() {
        assertFalse(DouyinProbeActivity.shouldTryImageFallback(hasVideoElement = true))
        assertTrue(DouyinProbeActivity.shouldTryImageFallback(hasVideoElement = false))
    }

    @Test
    fun domImagesTakePriorityOverBackgroundRequests() {
        assertEquals(
            listOf("https://p3-sign.douyinpic.com/current.webp"),
            DouyinProbeActivity.resolveImageCandidates(
                domImages = listOf("https://p3-sign.douyinpic.com/current.webp"),
                interceptedImages = listOf("https://p3-sign.douyinpic.com/recommendation.webp"),
            ),
        )
    }

    @Test
    fun interceptedTargetImagesRecoverWhenWebViewDomIsStillEmpty() {
        assertEquals(
            listOf("https://p3-sign.douyinpic.com/current.webp"),
            DouyinProbeActivity.resolveImageCandidates(
                domImages = emptyList(),
                interceptedImages = listOf("https://p3-sign.douyinpic.com/current.webp"),
            ),
        )
    }

    @Test
    fun neverSelectsDouyinExplicitWatermarkRendition() {
        assertEquals(
            listOf("https://p3-sign.douyinpic.com/tos-cn-i-0813/photo~tplv-dy-aweme-images.webp"),
            DouyinProbeActivity.resolveImageCandidates(
                domImages = listOf(
                    "https://p3-sign.douyinpic.com/tos-cn-i-0813/photo~tplv-dy-water-v2.webp",
                    "https://p3-sign.douyinpic.com/tos-cn-i-0813/photo~tplv-dy-aweme-images.webp",
                ),
                interceptedImages = emptyList(),
            ),
        )
    }

    @Test
    fun watermarkOnlyFallbackIsRejectedInsteadOfBeingDownloaded() {
        assertEquals(
            emptyList<String>(),
            DouyinProbeActivity.resolveImageCandidates(
                domImages = emptyList(),
                interceptedImages = listOf(
                    "https://p3-sign.douyinpic.com/tos-cn-i-0813/photo~tplv-dy-water-v2.webp",
                ),
            ),
        )
    }

    @Test
    fun waitsForTheCompleteGalleryInsteadOfFinishingFromTheFirstSlide() {
        assertEquals(
            emptyList<String>(),
            DouyinProbeActivity.resolveImageCandidates(
                domImages = listOf("https://p3-sign.douyinpic.com/first.webp"),
                interceptedImages = listOf("https://p3-sign.douyinpic.com/first.webp"),
                awaitingStructuredImages = true,
            ),
        )
    }

    @Test
    fun completeStructuredGalleryIsPreservedWithoutFirstImageTruncation() {
        val fullGallery = (1..14).map { index ->
            "https://p3-sign.douyinpic.com/tos-cn-i-0813/image-$index~tplv-dy-aweme-images.webp"
        }

        assertEquals(
            fullGallery,
            DouyinProbeActivity.resolveImageCandidates(
                domImages = fullGallery,
                interceptedImages = emptyList(),
                requiredCount = 14,
            ),
        )
    }

    @Test
    fun strictGalleryRejectsAFirstSlideWhenFourteenImagesAreDeclared() {
        assertEquals(
            emptyList<String>(),
            DouyinProbeActivity.resolveImageCandidates(
                domImages = listOf(
                    "https://p3-sign.douyinpic.com/first~tplv-dy-aweme-images.webp",
                ),
                interceptedImages = emptyList(),
                requiredCount = 14,
            ),
        )
    }

    @Test
    fun strictGalleryBlocksRangeBasedMediaEvenWithoutAnMp4Suffix() {
        assertTrue(
            DouyinProbeActivity.isAudibleMediaRequest(
                url = "https://v3-dy-o.zjcdn.com/tos-cn-v-001/opaque-resource",
                requestHeaders = mapOf("Range" to "bytes=0-"),
            ),
        )
    }

    @Test
    fun strictGalleryBlocksMediaDestinationAndDouyinVodHosts() {
        assertTrue(
            DouyinProbeActivity.isAudibleMediaRequest(
                url = "https://v3.douyinvod.com/tos-cn-v-001/resource",
                requestHeaders = emptyMap(),
            ),
        )
        assertTrue(
            DouyinProbeActivity.isAudibleMediaRequest(
                url = "https://example.test/opaque",
                requestHeaders = mapOf("Sec-Fetch-Dest" to "audio"),
            ),
        )
    }
}
