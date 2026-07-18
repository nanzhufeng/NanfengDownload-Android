package com.nanzhufeng.videodownloader.core.diagnostics

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorPresenterTest {
    @Test
    fun youtube403ExplainsThatCookiesOrTheTemporaryUrlMayHaveExpired() {
        val message = UserFacingErrorPresenter.message(
            rawError = "ERROR: HTTP Error 403: Forbidden",
            platform = DownloadPlatform.YOUTUBE,
        )

        assertEquals(
            "平台拒绝了下载请求（403），登录 Cookie 或临时下载地址可能已失效。" +
                "解决办法：请到“设置 → 账号与权限 → YouTube”重新导入最新 cookies.txt，" +
                "再重新智能读取并重试。",
            message,
        )
        assertFalse(message.contains("Forbidden"))
    }

    @Test
    fun interruptedStreamBecomesAChineseProblemAndAction() {
        val message = UserFacingErrorPresenter.message("unexpected end of stream")

        assertTrue(message.contains("网络传输中断"))
        assertTrue(message.contains("解决办法"))
        assertFalse(message.contains("unexpected end of stream"))
    }

    @Test
    fun ffmpegErrorsDoNotLeakEnglishIntoTheDefaultInterface() {
        val message = UserFacingErrorPresenter.message("FFmpeg exited with code 1 while merging formats")

        assertTrue(message.contains("音视频合并失败"))
        assertTrue(message.contains("降低分辨率"))
        assertFalse(message.contains("exited with code"))
    }

    @Test
    fun freshDouyinCookiesKeepsTheExistingActionableLoginInstruction() {
        assertEquals(
            "抖音需要新的网页会话。请到“设置 → 账号与权限 → 抖音”重新登录，返回后重试。",
            UserFacingErrorPresenter.message(
                "DownloadError: ERROR: [Douyin] Fresh cookies are needed",
                DownloadPlatform.DOUYIN,
            ),
        )
    }

    @Test
    fun unknownEnglishErrorUsesAChineseFallbackAndKeepsRawTextOutOfDefaultUi() {
        val message = UserFacingErrorPresenter.message("Extractor exploded with opaque upstream failure")

        assertTrue(message.contains("平台解析失败"))
        assertTrue(message.contains("原始错误"))
        assertFalse(message.contains("opaque upstream failure"))
    }
}
