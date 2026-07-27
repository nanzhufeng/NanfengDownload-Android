package com.nanzhufeng.videodownloader.core.diagnostics

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorPresenterTest {
    @Test
    fun `ssl hostname mismatch is not reported as generic timeout`() {
        val message = UserFacingErrorPresenter.message(
            "<urlopen error [SSL: CERTIFICATE_VERIFY_FAILED] certificate verify failed: " +
                "Hostname mismatch, certificate is not valid for 'b23.tv'>",
            platform = DownloadPlatform.BILIBILI,
        )

        assertTrue(message.contains("证书与平台域名不匹配"))
        assertTrue(message.contains("切换代理或 DNS"))
        assertFalse(message.contains("网络连接超时"))
    }

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

    @Test
    fun bilibili412ExplainsSessionOrRequestRejectionInChinese() {
        val message = UserFacingErrorPresenter.message(
            "HTTP Error 412: Precondition Failed",
            DownloadPlatform.BILIBILI,
        )

        assertTrue(message.contains("哔哩哔哩"))
        assertTrue(message.contains("登录"))
        assertTrue(message.contains("重新复制"))
        assertFalse(message.contains("Precondition"))
    }

    @Test
    fun bilibiliCreatorBoundaryHasDirectAlternative() {
        val message = UserFacingErrorPresenter.message(
            "bilibili creator batch is not supported",
            DownloadPlatform.BILIBILI,
        )

        assertTrue(message.contains("只支持单个视频"))
        assertTrue(message.contains("具体视频"))
        assertTrue(message.contains("智能读取"))
    }

    @Test
    fun xiaohongshuImageNoteAndExpiredTokenAreActionable() {
        val imageOnly = UserFacingErrorPresenter.message(
            "Xiaohongshu image-only note: no video formats",
            DownloadPlatform.XIAOHONGSHU,
        )
        val expired = UserFacingErrorPresenter.message(
            "Xiaohongshu xsec_token expired",
            DownloadPlatform.XIAOHONGSHU,
        )

        assertTrue(imageOnly.contains("图文笔记"))
        assertTrue(expired.contains("重新复制"))
        assertFalse(expired.contains("xsec_token"))
    }

    @Test
    fun mp3ValidationFailureExplainsCachedRetryWithoutLeakingRawDiagnostics() {
        val message = UserFacingErrorPresenter.message(
            "MP3 校验失败：没有检测到连续帧；bytes=123456；header=ID3-only",
            DownloadPlatform.YOUTUBE,
        )

        assertTrue(message.contains("没有通过完整性校验"))
        assertTrue(message.contains("复用已下载的转换源"))
        assertTrue(message.contains("不会重复下载"))
        assertFalse(message.contains("ID3-only"))
    }
}
