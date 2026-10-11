package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.*
import org.junit.Test

class DouyinShareVideoTest {
    @Test fun onlySafeCompleteDouyinVideoInputPreparesTransport() {
        assertTrue(DouyinReadPreparation.isEligible("复制 https://v.douyin.com/short_id/ 尾文"))
        assertTrue(DouyinReadPreparation.isEligible("https://www.douyin.com/video/123"))
        for (url in listOf("https://evil.example/video/123", "https://www.douyin.com/note/123",
                "http://v.douyin.com/short_id/", "https://v.douyin.com:444/short_id/",
                "https://user@v.douyin.com/short_id/", "https://v.douyin.com/")) {
            assertFalse(DouyinReadPreparation.isEligible(url))
        }
    }
    private fun page(id: String, url: String = "https://aweme.snssdk.com/aweme/v1/playwm/?video_id=test_id") =
        "<script>window._ROUTER_DATA = {\"loaderData\":{\"page\":{\"aweme_id\":\"$id\",\"desc\":\"target\",\"video\":{\"play_addr\":{\"url_list\":[\"$url\"]}}}}};</script>"

    @Test fun declaredTargetIsReadWithoutRunningPageScripts() {
        assertEquals("target", DouyinShareVideo.parse(page("123"), "123", 1000)?.title)
        assertNull(DouyinShareVideo.parse(page("456"), "123", 1000))
    }
    @Test fun noSourceCannotBecomeEarlySuccess() {
        assertNull(DouyinShareVideo.parse("<script>window._ROUTER_DATA={\"itemId\":\"123\"};</script>", "123", 1000))
        assertNull(DouyinShareVideo.parse(page("123", "https://evil.example/file.mp4"), "123", 1000))
        assertNull(DouyinShareVideo.parse(page("123", "https://media.douyinvod.com/video/tos/test.mp4?expire=1"), "123", 2000))
        assertNull(DouyinShareVideo.parse("window._ROUTER_DATA=broken", "123", 1000))
    }
    @Test fun officialPlaybackUsesSameDeclaredVideoIdWithoutReencodingQuery() {
        val url = "https://aweme.snssdk.com/aweme/v1/playwm/?video_id=test_id&ratio=720p&other=a%2Bb"
        assertEquals(url.replace("/playwm/", "/play/"), DouyinPlaybackUrl.normalize(url))
        assertEquals(url + "&video_id=other", DouyinPlaybackUrl.normalize(url + "&video_id=other"))
        assertEquals(url.replace("aweme.snssdk.com", "evil.example"), DouyinPlaybackUrl.normalize(url.replace("aweme.snssdk.com", "evil.example")))
    }
    @Test fun workIdentityIsAnOfficialRouteRatherThanTextAnywhereInUrl() {
        assertEquals("123", DouyinCaptureStore.extractWorkId("https://www.iesdouyin.com/share/video/123/"))
        assertNull(DouyinCaptureStore.extractWorkId("https://evil.example/video/123"))
        assertNull(DouyinCaptureStore.extractWorkId("https://www.douyin.com/?next=/video/123"))
        assertNull(DouyinCaptureStore.extractWorkId("https://www.douyin.com/video/123/trailing"))
    }
}
