package com.nanzhufeng.videodownloader.feature.settings

import java.io.File
import org.junit.Assert.assertFalse
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
        assertTrue(settingsSource.contains("SelectionContainer"))
        assertTrue(settingsSource.contains("settings-about-github-link"))
        assertTrue(settingsSource.contains("append(\"源码与更新： \")"))
        assertTrue(settingsSource.contains("append(RepositoryLabel)"))
        assertTrue(settingsSource.contains("LinkAnnotation.Url"))
        assertTrue(settingsSource.contains("url = RepositoryUrl"))
        assertTrue(settingsSource.contains("color = WarmOrange"))
        assertTrue(settingsSource.contains("textDecoration = TextDecoration.Underline"))
        assertTrue(settingsSource.contains("https://github.com/nanzhufeng/NanfengDownload-Android"))
        assertFalse(settingsSource.contains("源码与更新：GitHub"))
        assertFalse(settingsSource.contains("SettingsContent(key = \"feature-review\")"))
        assertFalse(settingsSource.contains("settings-feature-review-card"))
        assertTrue(expandedLayout.contains("settingsContent[4].content()"))
        assertTrue(gradleSource.contains("buildConfigField(\"String\", \"BUILD_TIMESTAMP\""))
        assertTrue(gradleSource.contains("ZoneId.of(\"Asia/Shanghai\")"))
    }
}
