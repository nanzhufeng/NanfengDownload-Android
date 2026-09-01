package com.nanzhufeng.videodownloader.feature.history

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalVideoPlayerChromeContractTest {
    @Test
    fun titleUsesTheSameVisibilityStateAsThePlayerControlsAndAvoidsTheStatusBar() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/feature/history/InternalVideoPlayerDialog.kt",
        ).readText()

        assertTrue(source.contains("setControllerVisibilityListener"))
        assertTrue(source.contains("controlsVisible = visibility == View.VISIBLE"))
        assertTrue(source.contains("if (controlsVisible)"))
        val playerView = source.substringAfter("AndroidView(").substringBefore("if (controlsVisible)")
        assertTrue(playerView.contains(".statusBarsPadding()"))
        assertTrue(source.contains("GestureDetector"))
        assertTrue(source.contains("override fun onDoubleTap"))
        assertTrue(source.contains("player.playWhenReady"))
        assertTrue(source.contains("setOnTouchListener"))
    }
}
