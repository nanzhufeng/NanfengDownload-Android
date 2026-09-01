package com.nanzhufeng.videodownloader.feature.history

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDetailsDialogContractTest {
    @Test
    fun detailsDialogUsesACompactInformationPanelAndIconifiedRealActions() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt",
        ).readText()
        val dialog = source.substringAfter("private fun HistoryDetailsDialog(").substringBefore("private fun PlayerChooserDialog(")

        assertTrue(dialog.contains("Dialog(onDismissRequest = onDismiss)"))
        assertTrue(dialog.contains("HistoryDetailsMetadataRow"))
        assertTrue(dialog.contains("HistoryDetailsActionButton"))
        assertTrue(dialog.contains("Icons.Filled.PlayCircle"))
        assertTrue(dialog.contains("Icons.Outlined.ContentCopy"))
        assertTrue(dialog.contains("Icons.AutoMirrored.Outlined.OpenInNew"))
        assertTrue(dialog.contains("Icons.Outlined.Description"))
        assertTrue(dialog.contains("内置播放器播放"))
        assertTrue(dialog.contains("复制原链接"))
        assertTrue(dialog.contains("查看吞吐报告"))
    }
}
