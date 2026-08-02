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
}
