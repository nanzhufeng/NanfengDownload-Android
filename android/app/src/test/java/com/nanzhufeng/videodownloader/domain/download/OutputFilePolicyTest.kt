package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputFilePolicyTest {
    private val policy = OutputFilePolicy()

    @Test
    fun videoPathUsesPlatformCreatorAndPublishedDate() {
        val output = policy.relativePath(
            media(
                platform = DownloadPlatform.DOUYIN,
                creator = "博主A",
                publishDate = "20260716",
                title = "标题",
            ),
            ResolutionPreset.UP_TO_720P,
        )

        assertEquals(
            "Movies/南烛枫视频下载器/抖音/博主A/2026-07-16 标题.mp4",
            output,
        )
    }

    @Test
    fun invalidPathCharactersAreReplacedWithoutLosingReadableText() {
        val output = policy.relativePath(
            media(
                platform = DownloadPlatform.YOUTUBE,
                creator = "频道:测试?",
                publishDate = "2026-07-16",
                title = "标题/第一集*",
            ),
            ResolutionPreset.BEST,
        )

        assertEquals(
            "Movies/南烛枫视频下载器/YouTube/频道_测试_/2026-07-16 标题_第一集_.mp4",
            output,
        )
    }

    @Test
    fun audioPresetUsesMp3Extension() {
        val output = policy.relativePath(
            media(platform = DownloadPlatform.TIKTOK),
            ResolutionPreset.AUDIO_MP3,
        )

        assertEquals(
            "Music/南烛枫视频下载器/TikTok/作者/2026-07-16 标题.mp3",
            output,
        )
    }

    private fun media(
        platform: DownloadPlatform,
        creator: String = "作者",
        publishDate: String = "20260716",
        title: String = "标题",
    ) = MediaItem(
        mediaKey = "${platform.name}:one",
        platform = platform,
        contentId = "one",
        originalUrl = "https://example.test/one",
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = title,
        creator = creator,
        creatorId = "creator-one",
        publishDate = publishDate,
        thumbnailUrl = "",
    )
}
