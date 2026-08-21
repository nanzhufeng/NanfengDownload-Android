package com.nanzhufeng.videodownloader.feature.history

import android.content.pm.ApplicationInfo
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlayerClassifierTest {
    @Test
    fun keepsSystemDefaultAndRealVideoPlayers() {
        assertTrue(isLikelyMediaPlayer("com.heytap.yoli", "SysVideoPlayActivity", -1, true))
        assertTrue(isLikelyMediaPlayer("org.videolan.vlc", "StartActivity", ApplicationInfo.CATEGORY_VIDEO, false))
        assertTrue(isLikelyMediaPlayer("com.mxtech.videoplayer.ad", "ActivityScreen", -1, false))
    }

    @Test
    fun rejectsAppsWhichOnlyClaimTheVideoMimeType() {
        assertFalse(isLikelyMediaPlayer("com.baidu.netdisk", "EnterShareFileActivity", -1, false))
        assertFalse(isLikelyMediaPlayer("com.tencent.androidqqmail", "LaunchComposeMail", -1, false))
        assertFalse(isLikelyMediaPlayer("com.heytap.browser", "FileDetailActivity", -1, false))
    }

    @Test
    fun audioHistoryUsesTheBuiltInPlayerWhileVideoKeepsTheSystemPlayer() {
        assertTrue(shouldUseInternalAudioPlayer(history(ResolutionPreset.AUDIO_MP3)))
        assertFalse(shouldUseInternalAudioPlayer(history(ResolutionPreset.UP_TO_720P)))
    }

    @Test
    fun mediaOpenMessagesDoNotCallMissingFilesAPlayerProblem() {
        assertEquals(
            "视频文件不存在或已无法读取。已在历史中标记，可重新读取原链接下载。",
            mediaOpenMessage(MediaOpenResult.MissingMedia, isAudio = false),
        )
        assertEquals(
            "没有可用的视频播放器。请安装或启用一个支持该文件的播放器后重试。",
            mediaOpenMessage(MediaOpenResult.NoPlayer, isAudio = false),
        )
    }

    private fun history(resolution: ResolutionPreset) = DownloadHistory(
        taskId = "task",
        platform = DownloadPlatform.YOUTUBE,
        contentId = "content",
        originalUrl = "https://www.youtube.com/watch?v=content",
        title = "示例",
        creator = "作者",
        resolution = resolution,
        finalStatus = DownloadTaskStatus.COMPLETED,
        outputUri = "content://media/item",
        fileSize = 1_024L,
        fileExists = true,
        completedAt = 1L,
    )
}
