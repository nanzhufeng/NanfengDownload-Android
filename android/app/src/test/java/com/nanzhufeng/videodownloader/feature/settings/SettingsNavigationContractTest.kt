package com.nanzhufeng.videodownloader.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationContractTest {
    @Test
    fun overviewUsesOnlyEntriesAndDetailsReturnToTheSameMenu() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt").readText()
        val overview = source.substringAfter("val settingsContent = listOf(")
            .substringBefore("private fun AboutSettingsContent(")
        assertFalse("设置首页不再直接摆放开关", overview.contains("SettingSwitchRow("))
        assertFalse("设置首页不再直接摆放单选项", overview.contains("RadioButton("))
        assertFalse("平台登录列表只在详情页", overview.contains("SessionRow("))
        for (tag in listOf("settings-accounts-entry", "settings-quality-entry", "settings-network-entry",
            "settings-background-island", "settings-concurrency", "settings-file-name",
            "settings-storage-location", "settings-export-cookies", "settings-about-entry")) {
            assertTrue("原设置入口不得遗漏: $tag", overview.contains(tag))
        }
        assertTrue("系统返回先退出设置详情", source.contains("BackHandler(enabled = section != null)"))
        assertTrue("返回后保留菜单滚动位置", overview.contains("state = overviewScroll"))
        assertTrue("展开视口限制阅读宽度", overview.contains("widthIn(max = 720.dp)"))
        assertTrue("菜单点击区保持足够高度", source.contains("heightIn(min = 52.dp)"))
    }
}
