package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinCaptureStoreTest {
    @Test
    fun capturesMediaOnlyFromTargetWorkPage() {
        DouyinCaptureStore.begin("https://www.douyin.com/video/7315041660419853608")
        DouyinCaptureStore.capture(
            pageUrl = "https://www.douyin.com/video/7315041660419853608",
            requestUrl = "https://v3-web.douyinvod.com/video/tos/cn/tos-cn-ve-15/o123.mp4",
        )

        assertTrue(DouyinCaptureStore.latestMediaUrl?.endsWith("o123.mp4") == true)
    }

    @Test
    fun capturesAnimatedNoteMediaOnlyFromTheTargetNotePage() {
        val pageUrl = "https://www.douyin.com/note/7674830543565405861"
        DouyinCaptureStore.begin(pageUrl)
        DouyinCaptureStore.capture(
            pageUrl = pageUrl,
            requestUrl = "https://v26-web.douyinvod.com/video/tos/cn/tos-cn-ve-15/animated-note?mime_type=video_mp4",
        )

        assertTrue(DouyinCaptureStore.latestMediaUrl?.contains("animated-note") == true)
        assertTrue(DouyinCaptureStore.latestPageUrl == pageUrl)
    }

    @Test
    fun rejectsMediaFromAnotherWorkPage() {
        DouyinCaptureStore.begin("https://www.douyin.com/video/7315041660419853608")
        DouyinCaptureStore.capture(
            pageUrl = "https://www.douyin.com/video/9999999999999999999",
            requestUrl = "https://v3-web.douyinvod.com/video/tos/cn/tos-cn-ve-15/other.mp4",
        )

        assertFalse(DouyinCaptureStore.latestMediaUrl != null)
    }

    @Test
    fun bindsShortShareToRedirectedWorkPage() {
        DouyinCaptureStore.begin("https://v.douyin.com/AbCdEfGh/")
        val pageUrl = "https://www.douyin.com/video/7315041660419853608"
        DouyinCaptureStore.updatePage(pageUrl)
        DouyinCaptureStore.capture(
            pageUrl = pageUrl,
            requestUrl = "https://v3-web.douyinvod.com/video/tos/cn/tos-cn-ve-15/redirected.mp4",
        )

        assertTrue(DouyinCaptureStore.latestMediaUrl?.endsWith("redirected.mp4") == true)
        assertTrue(DouyinCaptureStore.latestPageUrl == pageUrl)
    }

    @Test
    fun capturesDouyinVodCdnWithoutMp4Suffix() {
        val pageUrl = "https://www.douyin.com/video/7659318944100076838"
        DouyinCaptureStore.begin(pageUrl)
        DouyinCaptureStore.capture(
            pageUrl = pageUrl,
            requestUrl = "https://v3-web.douyinvod.com/abc/tos-cn-ve-15/oExample?mime_type=video_mp4",
        )

        assertTrue(DouyinCaptureStore.latestMediaUrl?.contains("douyinvod.com") == true)
    }

    @Test
    fun capturesMobileSharePlaywmUrl() {
        val pageUrl = "https://www.iesdouyin.com/share/video/7659318944100076838"
        DouyinCaptureStore.begin(pageUrl)
        DouyinCaptureStore.capture(
            pageUrl = pageUrl,
            requestUrl = "https://www.iesdouyin.com/aweme/v1/playwm/?ratio=720p&video_id=target",
        )

        assertTrue(DouyinCaptureStore.latestMediaUrl?.contains("/aweme/v1/playwm/") == true)
    }

    @Test
    fun rejectsMediaLikePathOnUntrustedHost() {
        val pageUrl = "https://www.douyin.com/video/7659318944100076838"
        DouyinCaptureStore.begin(pageUrl)
        DouyinCaptureStore.capture(
            pageUrl = pageUrl,
            requestUrl = "https://attacker.example/video/tos/cn/fake.mp4",
        )

        assertFalse(DouyinCaptureStore.latestMediaUrl != null)
    }

    @Test
    fun capturesImagePostUrlsOnlyFromTheTargetWorkPage() {
        val pageUrl = "https://www.douyin.com/video/7659318944100076838"
        DouyinCaptureStore.begin(pageUrl)

        val images = DouyinCaptureStore.captureImage(
            pageUrl = pageUrl,
            requestUrl = "https://p3-sign.douyinpic.com/tos-cn-i-0813/photo.webp?x-expires=1",
        )

        assertTrue(images.single().contains("douyinpic.com"))
        assertTrue(
            DouyinCaptureStore.captureImage(
                pageUrl = "https://www.douyin.com/video/7659318944100076999",
                requestUrl = "https://p3-sign.douyinpic.com/tos-cn-i-0813/other.webp?x-expires=1",
            ).isEmpty(),
        )
    }

    @Test
    fun retainsTargetPageImagesCapturedBeforeTheDomIsReady() {
        val pageUrl = "https://www.douyin.com/note/7659318944100076838"
        val imageUrl = "https://p3-sign.douyinpic.com/tos-cn-i-0813/note.webp?x-expires=1"
        DouyinCaptureStore.begin(pageUrl)

        DouyinCaptureStore.captureImage(pageUrl, imageUrl)

        assertTrue(DouyinCaptureStore.capturedImageUrls() == listOf(imageUrl))
    }

    @Test
    fun verifiedGalleryRequiresVersionExactCountAndOriginalTemplate() {
        val clean = List(14) { index ->
            "https://p3-sign.douyinpic.com/tos/image-$index~tplv-dy-aweme-images.webp"
        }

        assertTrue(
            DouyinCaptureStore.isVerifiedImageGallery(
                imageUrls = clean,
                expectedCount = 14,
                sourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
            ),
        )
        assertFalse(
            DouyinCaptureStore.isVerifiedImageGallery(
                imageUrls = clean.take(2),
                expectedCount = 14,
                sourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
            ),
        )
        assertFalse(
            DouyinCaptureStore.isVerifiedImageGallery(
                imageUrls = clean,
                expectedCount = 14,
                sourceVersion = 0,
            ),
        )
        assertFalse(
            DouyinCaptureStore.isVerifiedImageGallery(
                imageUrls = clean.dropLast(1) +
                    "https://p3-sign.douyinpic.com/tos/water~tplv-dy-water-v2.webp",
                expectedCount = 14,
                sourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
            ),
        )
    }
}
