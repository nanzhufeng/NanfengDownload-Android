package com.nanzhufeng.videodownloader.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAboutContractTest {
    @Test
    fun aboutCardUsesCurrentBuildMetadataAndVerifiedRepositoryIdentity() {
        val settingsSource = File(
            "src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt",
        ).readText()
        val gradleSource = File("build.gradle.kts").readText()
        val expandedLayout = settingsSource
            .substringAfter("if (expanded) {")
            .substringBefore("} else {")

        assertTrue(settingsSource.contains("SettingsContent(key = \"about\")"))
        assertTrue(settingsSource.contains("settings-about-card"))
        assertTrue(settingsSource.contains("BuildConfig.VERSION_NAME"))
        assertTrue(settingsSource.contains("BuildConfig.VERSION_CODE"))
        assertTrue(settingsSource.contains("BuildConfig.BUILD_TIMESTAMP"))
        assertTrue(settingsSource.contains("nanzhufeng/NanfengDownload-Android"))
        assertTrue(settingsSource.contains("关于与版本信息 · 保留"))
        assertTrue(expandedLayout.contains("settingsContent[5].content()"))
        assertTrue(gradleSource.contains("buildConfigField(\"String\", \"BUILD_TIMESTAMP\""))
        assertTrue(gradleSource.contains("ZoneId.of(\"Asia/Shanghai\")"))
    }
}
