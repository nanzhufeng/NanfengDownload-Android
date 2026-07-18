package com.nanzhufeng.videodownloader.feature.history

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
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
}
