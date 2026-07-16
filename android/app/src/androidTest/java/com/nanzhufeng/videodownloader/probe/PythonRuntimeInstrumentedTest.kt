package com.nanzhufeng.videodownloader.probe

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PythonRuntimeInstrumentedTest {
    @Test
    fun embeddedPythonLoadsPinnedYtDlp() {
        val info = YtDlpProbe().runtimeInfo()

        assertTrue(info.python.startsWith("3.13"))
        assertEquals("2026.06.09", info.ytDlp)
    }
}
