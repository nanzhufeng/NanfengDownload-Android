package com.nanzhufeng.videodownloader.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBrandingResourceTest {
    @Test
    fun everyUserFacingBrandEntryUsesTheSharedAppName() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val home = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt").readText()
        val navigation = File("src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt").readText()
        val worker = File("src/main/java/com/nanzhufeng/videodownloader/domain/download/ForegroundDownloadWorker.kt").readText()
        val buildScript = File("build.gradle.kts").readText()

        assertTrue(strings.contains("<string name=\"app_name\">南枫下载</string>"))
        assertTrue(home.contains("stringResource(R.string.app_name)"))
        assertTrue(navigation.contains("stringResource(R.string.app_name)"))
        assertTrue(worker.contains("getString(R.string.app_name)"))
        assertTrue(buildScript.contains("outputFileName = \"南枫下载.apk\""))
        assertFalse(home.contains("南烛枫视频下载器"))
        assertFalse(navigation.contains("南烛枫视频下载器"))
        assertFalse(worker.contains("南烛枫视频下载器"))
    }
}
