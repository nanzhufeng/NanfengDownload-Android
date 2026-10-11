package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenericWebCaptureTest {
    @Test
    fun dynamicPlayerRequiresHttpMediaAndStablePageIdentity() {
        val capture = GenericWebCapture(
            "https://example.test/work/42", "https://player.example.test/42",
            "https://cdn.example.test/42.m3u8", "目标作品",
        ).validate()
        assertEquals(capture.mediaId, capture.copy(mediaUrl = "https://cdn.example.test/renewed.m3u8").mediaId)
        assertThrows(IllegalArgumentException::class.java) {
            capture.copy(mediaUrl = "blob:https://example.test/one").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            capture.copy(mediaUrl = "https://cdn.example.test/manifest.mpd").validate()
        }
    }

    @Test
    fun officialPornhubCaptureKeepsTheSamePlatformAndViewkeyAsExtractor() {
        val page = "https://cn.pornhub.com/view_video.php?viewkey=sample123"
        val capture = GenericWebCapture(page, page, "https://cdn.example.test/target.mp4", "目标作品")
        assertEquals(DownloadPlatform.PORNHUB, capture.platform)
        assertEquals("sample123", capture.mediaId)
        assertEquals(true, canInspectWebPlayer(page))
        assertEquals(false, canInspectWebPlayer("https://cn.pornhub.com/users/other"))
    }

    @Test
    fun yinghuaParserKeepsOnlyCurrentWorkAndOrdersNumerically() {
        val episodes = YinghuaEpisodeParser.parse("https://www.yhdm.one/vod/42.html", listOf(
            "/vod-play/42/ep10.html", "/vod-play/99/ep1.html", "/vod-play/42/ep2.html",
            "/vod-play/42/yu_gao_pian.html", "https://ads.test/vod-play/42/ep1.html",
        ))
        assertEquals(listOf("第2集", "第10集"), episodes.map(GenericWebEpisode::title))
        assertEquals(2, episodes.map { genericCollectionMediaId(it.url) }.distinct().size)
        val captured = GenericWebCapture(
            "https://www.yhdm.one/vod-play/42/ep2.html",
            "https://www.yhdm.one/_player_x_/target",
            "https://cdn.example.test/target.m3u8", "目标作品 第2集",
        )
        assertEquals(genericCollectionMediaId(captured.pageUrl), captured.mediaId)
    }

    @Test
    fun yinghuaMixedWorksAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            YinghuaEpisodeParser.parse("https://www.yhdm.one/", listOf(
                "/vod-play/42/ep1.html", "/vod-play/99/ep1.html",
            ))
        }
    }
}
