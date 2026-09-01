package com.nanzhufeng.videodownloader.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRailLayoutContractTest {
    @Test
    fun expandedRailCentersTheThreePrimaryEntriesWithinItsOwnFixedWidth() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt",
        ).readText()
        val rail = source
            .substringAfter("private fun PrimaryNavigationRail")
            .substringBefore("private fun NavHostController.openPrimary")

        assertTrue(rail.contains(".fillMaxHeight()"))
        assertTrue(rail.contains(".width(80.dp)"))
        assertTrue(rail.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(rail.contains("modifier = Modifier.align(Alignment.Center)"))
        assertTrue(rail.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue(rail.contains(".align(Alignment.TopCenter)"))
    }
}
