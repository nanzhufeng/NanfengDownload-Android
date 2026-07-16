package com.nanzhufeng.videodownloader.feature.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeLayoutResourceTest {
    @Test
    fun compactQueuePanel_doesNotFillTheEntireAvailableHeight() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val queuePanel = source.substringAfter("private fun QueuePanel(")
            .substringBefore("private fun QueueRow(")

        assertFalse(
            "空队列不能用 fillMaxSize 撑满外屏剩余空间",
            queuePanel.contains("Column(modifier = Modifier.fillMaxSize())"),
        )
    }
}
