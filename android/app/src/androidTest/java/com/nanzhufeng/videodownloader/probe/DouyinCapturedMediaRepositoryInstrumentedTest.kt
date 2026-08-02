package com.nanzhufeng.videodownloader.probe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DouyinCapturedMediaRepositoryInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val preferencesName = "douyin_capture_test_${System.nanoTime()}"
    private var nowMillis = 1_000_000L

    @Before
    fun clearBeforeTest() {
        context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun clearAfterTest() {
        context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun capturePersistsAcrossRepositoryInstancesWithoutStoringCookies() {
        val captured = trustedCapture(capturedAtMillis = nowMillis)
        repository().save(captured)

        assertEquals(captured, repository().find(captured.workId))
        val rawPayload = context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .getString(captured.workId, "")
            .orEmpty()
        assertFalse(rawPayload.contains("cookie", ignoreCase = true))
        assertFalse(rawPayload.contains("sessionid", ignoreCase = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun attackerControlledMediaHostIsRejected() {
        repository().save(
            trustedCapture(capturedAtMillis = nowMillis).copy(
                mediaUrl = "https://douyin.com.attacker.example/video.mp4",
            ),
        )
    }

    @Test
    fun expiredCaptureIsRemovedInsteadOfBeingReused() {
        val captured = trustedCapture(capturedAtMillis = nowMillis)
        repository().save(captured)
        nowMillis += 6 * 60 * 60 * 1_000L + 1

        assertNull(repository().find(captured.workId))
        assertNull(
            context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
                .getString(captured.workId, null),
        )
    }

    private fun repository() = DouyinCapturedMediaRepository(
        context = context,
        nowMillis = { nowMillis },
        preferencesName = preferencesName,
    )

    private fun trustedCapture(capturedAtMillis: Long) = DouyinCapturedMedia(
        workId = "7669248142533973995",
        pageUrl = "https://www.iesdouyin.com/share/video/7669248142533973995/",
        mediaUrl = "https://v3.douyinvod.com/video/tos/cn/tos-cn-ve-15/video.mp4",
        title = "测试作品",
        creator = "测试作者",
        thumbnailUrl = "https://p3-sign.douyinpic.com/tos-cn-p-0015/image.jpeg",
        capturedAtMillis = capturedAtMillis,
    )
}
