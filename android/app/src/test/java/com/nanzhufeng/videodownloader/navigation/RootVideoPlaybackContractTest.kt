package com.nanzhufeng.videodownloader.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootVideoPlaybackContractTest {
    @Test
    fun videoPlaybackIsOwnedByTheAppRootInsteadOfTheHistoryDestination() {
        val appSource = File(
            "src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt",
        ).readText()
        val historySource = File(
            "src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt",
        ).readText()

        assertTrue(appSource.contains("var activeVideoTaskId by rememberSaveable"))
        assertTrue(appSource.contains("InternalVideoPlayerOverlay("))
        assertTrue(appSource.contains("onOpenHistoryVideo = { taskId, uri, title ->"))
        assertTrue(historySource.contains("onOpenInternalVideo = onOpenInternalVideo"))
        assertFalse(historySource.contains("InternalVideoPlayerOverlay("))
    }
}
