package com.nanzhufeng.videodownloader.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFeedbackResourceTest {
    @Test
    fun aNewCompletedDownloadShowsAnExplicitSuccessDialog() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt")
            .readText()

        assertTrue(source.contains("downloads.history.collect"))
        assertTrue(source.contains("AlertDialog("))
        assertTrue(source.contains("containerColor = Color.White"))
        assertTrue(source.contains("下载成功"))
        assertTrue(source.contains("completion-dialog"))
    }
}
