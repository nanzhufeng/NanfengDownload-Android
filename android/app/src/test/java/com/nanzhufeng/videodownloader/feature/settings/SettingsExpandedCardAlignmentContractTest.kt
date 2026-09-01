package com.nanzhufeng.videodownloader.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExpandedCardAlignmentContractTest {
    @Test
    fun firstTwoExpandedRowsFillBothCardsToTheTallerCardsHeight() {
        val source = File(
            "src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt",
        ).readText()
        val expandedLayout = source
            .substringAfter("if (expanded) {")
            .substringBefore("} else {")
        val sessionRow = source
            .substringAfter("private fun SessionRow(")
            .substringBefore("private fun SettingSwitchRow(")

        assertTrue(expandedLayout.contains("SettingsCardPair("))
        assertTrue(expandedLayout.contains("left = settingsContent[0].content"))
        assertTrue(expandedLayout.contains("right = settingsContent[1].content"))
        assertTrue(expandedLayout.contains("left = settingsContent[2].content"))
        assertTrue(expandedLayout.contains("right = settingsContent[3].content"))
        assertFalse(expandedLayout.contains("LazyVerticalGrid"))
        assertTrue(source.contains(".height(IntrinsicSize.Min)"))
        assertTrue(source.contains("Modifier.weight(1f).fillMaxHeight()"))
        assertTrue(source.contains("fillSettingsRowHeightIf(expanded)"))
        assertFalse(sessionRow.contains("OutlinedButton("))
        assertTrue(sessionRow.contains("containerColor = Color(0xFFF1F3F2)"))
    }
}
