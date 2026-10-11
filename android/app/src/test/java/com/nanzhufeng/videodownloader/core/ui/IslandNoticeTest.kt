package com.nanzhufeng.videodownloader.core.ui

import org.junit.Assert.*
import org.junit.Test

class IslandNoticeTest {
    @Test fun oldTimerCannotDismissNewFeedback() {
        val state = IslandNoticeState()
        val first = state.present(IslandNotice("已复制"))
        val latest = IslandNotice("无法打开", "请安装可用的视频播放器", IslandTone.ERROR)
        val second = state.present(latest)
        assertFalse(state.dismiss(first))
        assertEquals(latest, state.current)
        assertTrue(state.dismiss(second))
        assertNull(state.current)
    }
    @Test fun dismissingAnAlreadyClosedNoticeIsANoop() {
        val state = IslandNoticeState()
        val token = state.present(IslandNotice("已复制"))
        assertTrue(state.dismiss(token))
        assertFalse(state.dismiss(token))
    }
    @Test fun sameMessageAgainGetsItsOwnReadingWindow() {
        val state = IslandNoticeState()
        val notice = IslandNotice("已复制原链接", tone = IslandTone.SUCCESS)
        val first = state.present(notice)
        val second = state.present(notice)
        assertTrue(second > first)
        assertFalse(state.dismiss(first))
    }
    @Test fun longErrorsKeepEveryCharacterAndReceiveMoreReadingTime() {
        val message = "请确认目标文件可写，重新选择保存位置后重试。".repeat(30)
        val notice = IslandNotice("导出未完成", message, IslandTone.ERROR)
        assertEquals(message, notice.detail)
        assertTrue(notice.spokenText.endsWith(message))
        assertEquals(12_000L, notice.readingMillis)
        assertTrue(notice.readingMillis > IslandNotice("已复制").readingMillis)
    }
}
