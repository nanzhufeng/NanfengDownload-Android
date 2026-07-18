package com.nanzhufeng.videodownloader.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotionContractTest {
    @Test
    fun primaryPagesSwitchImmediatelyWithoutCrossfadeOrSlide() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt",
        ).readText()

        assertTrue(source.contains("enterTransition = { EnterTransition.None }"))
        assertTrue(source.contains("exitTransition = { ExitTransition.None }"))
        assertTrue(source.contains("popEnterTransition = { EnterTransition.None }"))
        assertTrue(source.contains("popExitTransition = { ExitTransition.None }"))
        assertFalse(source.contains("fadeIn("))
        assertFalse(source.contains("fadeOut("))
        assertFalse(source.contains("slideIntoContainer("))
        assertFalse(source.contains("slideOutOfContainer("))
    }
}
