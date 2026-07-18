package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlClassifierTest {
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
    fun missingUrlFails() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlClassifier.extractAndClassify("只有普通文字")
        }
    }
}
