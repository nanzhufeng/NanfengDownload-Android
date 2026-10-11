package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinCaptureReusePolicyTest {
    @Test fun sameAccountCookieRenewalDoesNotInvalidateARecentSource() {
        val before = DouyinCaptureContext.sessionFingerprint(listOf("sessionid=test;sid_guard=guard%7C1%7C2"))
        val renewed = DouyinCaptureContext.sessionFingerprint(listOf("sessionid=test;sid_guard=guard%7C9%7C10"))
        val scoped = capture.copy(sessionFingerprint = before)
        assertTrue(DouyinCaptureReusePolicy.canReuse(scoped, source, 2000, renewed))
        val switched = DouyinCaptureContext.sessionFingerprint(listOf("sessionid=other;sid_guard=guard%7C9%7C10"))
        assertFalse(DouyinCaptureReusePolicy.canReuse(scoped, source, 2000, switched))
    }
    @Test fun videoCacheCannotBypassStrictNoteGallery() {
        val note = source.replace("/video/", "/note/")
        assertFalse(reuse(capture.copy(pageUrl = note), note, 2_000))
        assertFalse(reuse(capture, note, 2_000))
    }

    @Test fun legacyProfileAndChangedLoginSessionMustReload() {
        assertFalse(reuse(capture.copy(captureUserAgent = "", sessionFingerprint = ""), source, 2_000))
        val a = DouyinCaptureContext.sessionFingerprint(listOf("sessionid=a"))
        val b = DouyinCaptureContext.sessionFingerprint(listOf("sessionid=b"))
        val scoped = capture.copy(captureUserAgent = "Mobile Browser", sessionFingerprint = a)
        assertTrue(reuse(scoped, source, 2_000, a))
        assertFalse(reuse(scoped, source, 2_000, b))
    }
    private val fingerprint = DouyinCaptureContext.sessionFingerprint(emptyList())
    private fun reuse(media: DouyinCapturedMedia, url: String, now: Long, context: String = fingerprint) =
        DouyinCaptureReusePolicy.canReuse(media, url, now, context)
    private val source = "https://www.douyin.com/video/7684884186910329202"
    private val capture = DouyinCapturedMedia(
        workId = "7684884186910329202", pageUrl = source,
        mediaUrl = "https://v26-weba.douyinvod.com/video/tos/cn/sample/",
        title = "sample", creator = "creator", thumbnailUrl = "", capturedAtMillis = 1_000, captureUserAgent = "Mobile Browser", sessionFingerprint = fingerprint,
    )

    @Test fun recentVerifiedTargetCanReturnWithoutOpeningThePage() {
        assertTrue(reuse(capture, source, 121_000))
    }

    @Test fun expiredOrFutureDatedCaptureMustReload() {
        assertFalse(reuse(capture, source, 121_001))
        assertFalse(reuse(capture, source, 999))
    }

    @Test fun differentTargetUntrustedPageAndBlobCannotBeReused() {
        assertFalse(reuse(capture, source.replace("7684884186910329202", "7690158572814077184"), 2_000))
        assertFalse(reuse(capture.copy(pageUrl = "https://evil.example/video/7684884186910329202"), source, 2_000))
        assertFalse(reuse(capture, "https://douyin.com.evil.example/video/7684884186910329202", 2_000))
        assertFalse(reuse(capture.copy(mediaUrl = "blob:$source"), source, 2_000))
    }

    @Test fun onlyCompleteVerifiedNoteGalleryCanBeReused() {
        val note = source.replace("/video/", "/note/")
        val image = "https://p3-sign.douyinpic.com/tos-cn-i-0813/a~tplv-dy-aweme-images.webp"
        val gallery = capture.copy(pageUrl = note, mediaUrl = "", imageUrls = listOf(image), imageExpectedCount = 1, imageSourceVersion = 1)
        assertTrue(reuse(gallery, note, 2_000))
        assertFalse(reuse(gallery.copy(imageExpectedCount = 2), note, 2_000))
        assertFalse(reuse(gallery.copy(imageSourceVersion = 0), note, 2_000))
    }
}
