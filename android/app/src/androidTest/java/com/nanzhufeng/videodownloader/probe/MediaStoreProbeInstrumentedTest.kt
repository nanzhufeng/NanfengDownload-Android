package com.nanzhufeng.videodownloader.probe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreProbeInstrumentedTest {
    @Test
    fun writesProbeFileToSharedMovies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val input = File(context.cacheDir, "storage-probe.mp4")
        input.writeBytes(ByteArray(70 * 1024) { index -> (index % 251).toByte() })

        val uri = MediaStoreProbe.writeVideo(context, input, "storage-probe.mp4")
        val descriptor = requireNotNull(
            context.contentResolver.openAssetFileDescriptor(uri, "r"),
        ) { "无法重新打开已写入的媒体文件" }
        val size = descriptor.use { it.length }

        assertTrue(size >= input.length())
    }
}
