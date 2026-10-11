package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.*
import org.junit.Test

class PornhubPageVideoTest {
    private val page = "https://cn.pornhub.com/view_video.php?viewkey=fixture123"
    private fun html(target: String = "fixture123", mediaHost: String = "hv-h.phncdn.com", unavailable: Boolean = false) = """
        <meta property="og:url" content="https://cn.pornhub.com/view_video.php?viewkey=$target">
        <meta name="twitter:title" content="Synthetic title &amp; 中文">
        <script>var flashvars_321 = {"embedCode":"https://www.pornhub.com/embed/$target","video_unavailable":$unavailable,
          "mediaDefinitions":[{"format":"hls","quality":"1080","videoUrl":"https://$mediaHost/1080/index.m3u8"},
          {"format":"hls","quality":"720","videoUrl":"https://$mediaHost/720/index.m3u8"}]};</script>
    """.trimIndent()
    @Test fun readsTheExactOfficialPlayerWithoutFetchingEveryManifest() {
        val result = requireNotNull(PornhubPageVideo.parse(html(), page, ResolutionPreset.UP_TO_720P))
        assertEquals("fixture123", result.id)
        assertEquals("pornhub", result.platform)
        assertEquals("Synthetic title & 中文", result.title)
        assertEquals(page, result.webpageUrl)
        assertEquals("https://hv-h.phncdn.com/720/index.m3u8", result.videoUrl)
        assertFalse(result.headers.containsKey("Cookie"))
    }
    @Test fun rejectsForeignOrUnavailablePlayersAndUntrustedStreams() {
        assertNull(PornhubPageVideo.parse(html("other"), page, ResolutionPreset.BEST))
        assertNull(PornhubPageVideo.parse(html(unavailable=true), page, ResolutionPreset.BEST))
        assertNull(PornhubPageVideo.parse(html(mediaHost="phncdn.com.evil.test"), page, ResolutionPreset.BEST))
        assertNull(PornhubPageVideo.parse(html().replace("/embed/fixture123", "/embed/other"), page, ResolutionPreset.BEST))
    }
    @Test fun rejectsAmbiguousOrNonOfficialWorkUrls() {
        for (url in listOf(page.replace("cn.pornhub.com", "pornhub.com.evil.test"), page.replace("https:", "http:"),
            page + "&viewkey=other", page.replace("cn.pornhub.com", "cn.pornhub.com:8443"))) {
            assertNull(PornhubPageVideo.parse(html(), url, ResolutionPreset.BEST))
        }
    }
    @Test fun doesNotTurnAnEmptyPageOrAnExpiredStreamIntoSuccess() {
        assertNull(PornhubPageVideo.parse("<html>login</html>", page, ResolutionPreset.BEST))
        assertNull(PornhubPageVideo.parse(html().replace("index.m3u8", "index.m3u8?expire=1"), page, ResolutionPreset.BEST))
    }
}
