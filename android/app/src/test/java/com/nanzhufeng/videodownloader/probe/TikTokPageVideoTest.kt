package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TikTokPageVideoTest {
    private val page = "https://www.tiktok.com/@fixture/video/1234567890"
    private fun html(id: String = "1234567890", status: Int = 0, source: String = "https://v16-webapp.tiktok.com/video.mp4") =
        "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">" +
            JSONObject(mapOf("__DEFAULT_SCOPE__" to mapOf("webapp.video-detail" to mapOf(
                "statusCode" to status, "itemInfo" to mapOf("itemStruct" to mapOf("id" to id, "desc" to "fixture", "video" to mapOf("playAddr" to source, "width" to 720, "height" to 1280)))
            )))).toString() + "</script>"
    @Test fun acceptsOnlyMatchingOfficialTargetAndKeepsMediaHeaders() {
        val result = requireNotNull(TikTokPageVideo.parse(html(), page, ResolutionPreset.UP_TO_720P))
        assertEquals("1234567890", result.id)
        assertEquals("tiktok", result.platform)
        assertEquals(page, result.headers["Referer"])
        assertEquals(TikTokPageVideo.USER_AGENT, result.headers["User-Agent"])
        assertFalse(result.headers.containsKey("Cookie"))
    }
    @Test fun rejectsOtherWorkAndFailedOrMissingTargetState() {
        assertNull(TikTokPageVideo.parse(html(id="1234567891"), page, ResolutionPreset.BEST))
        assertNull(TikTokPageVideo.parse(html(status=10204), page, ResolutionPreset.BEST))
        assertNull(TikTokPageVideo.parse(html().replace("\"statusCode\":0", "\"otherStatus\":0"), page, ResolutionPreset.BEST))
        assertNull(TikTokPageVideo.parse("<html>login</html>", page, ResolutionPreset.BEST))
    }
    @Test fun rejectsForeignOrUnsafeSourceAndExpiredMedia() {
        for (source in listOf("https://tiktok.com.evil.test/video.mp4", "http://v16.tiktok.com/video.mp4", "https://user@v16.tiktok.com/video.mp4", "https://v16.tiktok.com:444/video.mp4", "https://v16.tiktok.com/video.mp4?expires=1")) {
            assertNull(TikTokPageVideo.parse(html(source=source), page, ResolutionPreset.BEST))
        }
    }
    @Test fun rejectsSpoofedPageAndNonDefaultPort() {
        for (url in listOf(page.replace("www.tiktok.com", "www.tiktok.com.evil.test"), page.replace("www.tiktok.com", "www.tiktok.com:444"), page.replace("https:", "http:"), page.replace("/@", "/user/"))) assertNull(TikTokPageVideo.workId(url))
    }
    @Test fun audioRequestUsesCompleteProgressiveSource() {
        val result = requireNotNull(TikTokPageVideo.parse(html(), page, ResolutionPreset.AUDIO_MP3))
        assertTrue(result.audioFromVideoSource)
        assertNull(result.audioUrl)
    }
    @Test fun mobileReflowDetailMustStillMatchExactTarget() {
        val reflow = html().replace("webapp.video-detail", "webapp.reflow.video.detail")
        assertEquals("1234567890", requireNotNull(TikTokPageVideo.parse(reflow, page, ResolutionPreset.BEST)).id)
        assertNull(TikTokPageVideo.parse(reflow.replace("1234567890", "1234567891"), page, ResolutionPreset.BEST))
    }
    @Test fun scopesResponseCookiesToTheMediaHostAndDropsExpiredOrForeignCookies() {
        val info = requireNotNull(TikTokPageVideo.parse(html(), page, ResolutionPreset.BEST))
        fun cookie(name: String, domain: String, expires: Long = Long.MAX_VALUE) = okhttp3.Cookie.Builder()
            .name(name).value("fixture").domain(domain).path("/").secure().expiresAt(expires).build()
        val bound = TikTokPageVideo.bindMediaCookies(info, listOf(cookie("scoped", "tiktok.com"),
            cookie("foreign", "example.test"), cookie("expired", "tiktok.com", 1)), 1000)
        assertEquals("scoped=fixture", bound.videoCookieHeader)
        assertFalse(bound.headers.containsKey("Cookie"))
        assertEquals("", TikTokPageVideo.bindMediaCookies(info.copy(videoUrl="https://media.tiktokcdn.com/video.mp4"), listOf(cookie("scoped", "tiktok.com")), 1000).videoCookieHeader)
    }
}
