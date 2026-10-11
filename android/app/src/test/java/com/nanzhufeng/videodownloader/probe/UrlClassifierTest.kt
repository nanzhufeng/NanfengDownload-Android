package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlClassifierTest {
    @Test
    fun pornhubWorkKeepsItsOwnPlatformAndSource() {
        val url = "https://cn.pornhub.com/view_video.php?viewkey=sample123"
        val source = UrlClassifier.extractAndClassify(url)
        assertEquals("PORNHUB", source.platform.name)
        assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
        assertEquals(url, source.url)
    }

    @Test
    fun pornhubImpersonationAndEmbeddedCredentialsAreRejected() {
        val impostor = UrlClassifier.extractAndClassify(
            "https://pornhub.com.attacker.test/view_video.php?viewkey=sample123",
        )
        assertEquals(Platform.GENERIC, impostor.platform)
        listOf(
            "https://user:pass@www.pornhub.com/view_video.php?viewkey=sample123",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { UrlClassifier.extractAndClassify(url) }
        }
        assertEquals(
            SourceKind.UNKNOWN_WEB_PAGE,
            UrlClassifier.extractAndClassify("https://www.pornhub.com/view_video.php").kind,
        )
    }

    @Test
    fun publicWebMediaAndWorkPagesUseGenericSource() {
        val direct = UrlClassifier.extractAndClassify("https://media.example.test/clip.mp4")
        assertEquals("GENERIC", direct.platform.name)
        assertEquals(SourceKind.SINGLE_VIDEO, direct.kind)
        val page = UrlClassifier.extractAndClassify("https://vimeo.com/227128119")
        assertEquals("GENERIC", page.platform.name)
        assertEquals("UNKNOWN_WEB_PAGE", page.kind.name)
    }

    @Test
    fun multipleUrlsKeepOrderAndRemoveDuplicates() {
        assertEquals(
            listOf("https://a.test/one.mp4", "https://b.test/two.m3u8"),
            UrlClassifier.extractUrls("第一个 https://a.test/one.mp4， 第二个 https://b.test/two.m3u8 https://a.test/one.mp4"),
        )
    }

    @Test
    fun youtubeWatchIsSingleVideo() {
        val source = UrlClassifier.extractAndClassify(
            "https://www.youtube.com/watch?v=abcdefghijk",
        )

        assertEquals(Platform.YOUTUBE, source.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
    }

    @Test
    fun youtubeLiveRouteIsSingleVideo() {
        val source = UrlClassifier.extractAndClassify(
            "https://www.youtube.com/live/Z98F3gyNFqM",
        )

        assertEquals(Platform.YOUTUBE, source.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
    }

    @Test
    fun douyinShareTextExtractsShortUrl() {
        val source = UrlClassifier.extractAndClassify(
            "复制打开抖音，看看TA的更多作品。 https://v.douyin.com/AbCdEfGh/",
        )

        assertEquals(Platform.DOUYIN, source.platform)
        assertEquals(SourceKind.UNKNOWN_DOUYIN_SHARE, source.kind)
        assertEquals("https://v.douyin.com/AbCdEfGh/", source.url)
    }

    @Test
    fun douyinNoteIsAcceptedAsSingleWork() {
        val source = UrlClassifier.extractAndClassify(
            "https://www.douyin.com/note/7670887343922973155",
        )

        assertEquals(Platform.DOUYIN, source.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
    }

    @Test
    fun youtubeChannelIsNotSingleVideo() {
        val source = UrlClassifier.extractAndClassify("https://www.youtube.com/@example/videos")

        assertEquals(SourceKind.CHANNEL_OR_PLAYLIST, source.kind)
    }

    @Test
    fun tiktokVideoIsSingleVideo() {
        val source = UrlClassifier.extractAndClassify(
            "https://www.tiktok.com/@creator/video/7512345678901234567",
        )

        assertEquals(Platform.TIKTOK, source.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
    }

    @Test
    fun tiktokCreatorIsCatalog() {
        val source = UrlClassifier.extractAndClassify("https://www.tiktok.com/@creator")

        assertEquals(Platform.TIKTOK, source.platform)
        assertEquals(SourceKind.CHANNEL_OR_PLAYLIST, source.kind)
    }

    @Test
    fun tiktokShortLinkDefersNetworkClassification() {
        val source = UrlClassifier.extractAndClassify("https://vt.tiktok.com/ZSMock123/")

        assertEquals(Platform.TIKTOK, source.platform)
        assertEquals(SourceKind.UNKNOWN_TIKTOK_SHARE, source.kind)
    }

    @Test
    fun tiktokCurrentShareRouteIsAcceptedForSafeCanonicalizationByTheProbe() {
        val source = UrlClassifier.extractAndClassify("https://www.tiktok.com/t/ZTDPhcpvK/")

        assertEquals(Platform.TIKTOK, source.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
    }

    @Test
    fun iesDouyinOfficialShareRoutesAreAccepted() {
        val video = UrlClassifier.extractAndClassify(
            "https://www.iesdouyin.com/share/video/7669248142533973995/",
        )
        val note = UrlClassifier.extractAndClassify(
            "https://www.iesdouyin.com/share/note/7670887343922973155/",
        )
        val creator = UrlClassifier.extractAndClassify(
            "https://www.iesdouyin.com/share/user/MS4wLjABAAAAcurrent/",
        )

        assertEquals(SourceKind.SINGLE_VIDEO, video.kind)
        assertEquals(SourceKind.SINGLE_VIDEO, note.kind)
        assertEquals(SourceKind.CHANNEL_OR_PLAYLIST, creator.kind)
    }

    @Test
    fun bilibiliVideoIsClassifiedAndCreatorHasHonestBoundary() {
        val video = UrlClassifier.extractAndClassify("https://www.bilibili.com/video/BV1bK411W797")

        assertEquals(Platform.BILIBILI, video.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, video.kind)
        val error = assertThrows(IllegalArgumentException::class.java) {
            UrlClassifier.extractAndClassify("https://space.bilibili.com/12345/video")
        }
        assertTrue(error.message.orEmpty().contains("暂不支持从 UP 主页批量读取"))
    }

    @Test
    fun bilibiliShortLinkDefersNetworkClassification() {
        val source = UrlClassifier.extractAndClassify("https://b23.tv/AbCd123")

        assertEquals(Platform.BILIBILI, source.platform)
        assertEquals(SourceKind.UNKNOWN_BILIBILI_SHARE, source.kind)
    }

    @Test
    fun xiaohongshuAndRednoteVideoRoutesAreClassified() {
        val xiaohongshu = UrlClassifier.extractAndClassify(
            "https://www.xiaohongshu.com/explore/69ce30d3000000002100791c?xsec_token=fresh",
        )
        val rednote = UrlClassifier.extractAndClassify(
            "https://www.rednote.com/explore/69ce30d3000000002100791c?xsec_token=fresh",
        )

        assertEquals(Platform.XIAOHONGSHU, xiaohongshu.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, xiaohongshu.kind)
        assertEquals(Platform.XIAOHONGSHU, rednote.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, rednote.kind)
    }

    @Test
    fun xiaohongshuShortLinkDefersNetworkClassification() {
        val legacy = UrlClassifier.extractAndClassify("https://xhslink.com/a/AbCd123")
        val current = UrlClassifier.extractAndClassify("https://xhslink.cn/o/7i6agytmp2s")

        assertEquals(Platform.XIAOHONGSHU, legacy.platform)
        assertEquals(SourceKind.UNKNOWN_XIAOHONGSHU_SHARE, legacy.kind)
        assertEquals(Platform.XIAOHONGSHU, current.platform)
        assertEquals(SourceKind.UNKNOWN_XIAOHONGSHU_SHARE, current.kind)
    }

    @Test
    fun platformNameInForeignHostDoesNotBypassHostValidation() {
        val source = UrlClassifier.extractAndClassify(
            "https://example.test/redirect?next=https://www.bilibili.com/video/BV1bK411W797",
        )
        assertEquals(Platform.GENERIC, source.platform)
        assertEquals(SourceKind.UNKNOWN_WEB_PAGE, source.kind)
    }

    @Test
    fun missingUrlFails() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlClassifier.extractAndClassify("只有普通文字")
        }
    }
}
