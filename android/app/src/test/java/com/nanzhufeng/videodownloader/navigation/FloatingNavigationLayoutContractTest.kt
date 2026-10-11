package com.nanzhufeng.videodownloader.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingNavigationLayoutContractTest {
    @Test
    fun compactAndExpandedShareTheSameFloatingNavigation() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt",
        ).readText()
        val destinations = File(
            "src/main/java/com/nanzhufeng/videodownloader/navigation/AppDestination.kt",
        ).readText()
        val home = File(
            "src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt",
        ).readText()

        assertTrue(source.contains("bottomBar = {\n                        PrimaryFloatingNavigation(navController, route, compact = true)"))
        assertFalse(source.contains("PrimaryBottomNavigation("))
        assertTrue(destinations.contains("HOME(\"home\", \"首页\""))
        assertFalse(home.contains("添加视频或链接开始下载，任务在本机处理，安全高效"))
    }

    @Test
    fun expandedNavigationIsAnIndependentFloatingBottomPill() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt",
        ).readText()
        val expanded = source
            .substringAfter("if (expanded) {")
            .substringBefore("\n            } else {\n                Scaffold(")
        val floating = source
            .substringAfter("private fun PrimaryFloatingNavigation")
            .substringBefore("private fun NavHostController.openPrimary")

        assertTrue(expanded.contains("Column(modifier = Modifier.fillMaxSize())"))
        assertTrue(expanded.contains("modifier = Modifier.weight(1f)"))
        assertTrue(expanded.contains("PrimaryFloatingNavigation(navController, route, compact = false)"))
        assertFalse(expanded.contains("PrimaryNavigationRail("))
        assertTrue(floating.contains(".navigationBarsPadding()"))
        assertTrue(floating.contains(".widthIn(max = 720.dp)"))
        assertTrue(floating.contains(".shadow("))
        assertTrue(floating.contains("AppDestination.entries.forEach"))
    }

    @Test
    fun compactNavigationSavesContentHeightWithoutRemovingSystemGestureInset() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt",
        ).readText()
        val floating = source
            .substringAfter("private fun PrimaryFloatingNavigation")
            .substringBefore("private fun NavHostController.openPrimary")

        assertTrue(floating.contains("val itemHeight = if (compact) 52.dp else 56.dp"))
        assertTrue(floating.contains("val bottomGap = if (compact) 4.dp else 6.dp"))
        assertTrue(floating.contains(".navigationBarsPadding()"))
        assertTrue(floating.contains(".height(itemHeight)"))
        assertFalse(floating.contains(".height(60.dp)"))
    }
}
