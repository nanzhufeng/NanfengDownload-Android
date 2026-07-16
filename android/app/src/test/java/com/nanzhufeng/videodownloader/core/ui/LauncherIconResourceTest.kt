package com.nanzhufeng.videodownloader.core.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourceTest {
    @Test
    fun launcherIcon_usesTransparentAdaptiveIconInsteadOfLegacyWhitePlate() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val adaptiveIcon = File("src/main/res/mipmap-anydpi-v26/nanzhufeng_app_icon.xml")
        val foreground = File("src/main/res/drawable/nanzhufeng_launcher_foreground.xml")

        assertTrue(
            "桌面图标必须使用自适应图标资源，避免系统给普通 PNG 套白色圆底",
            manifest.contains("android:icon=\"@mipmap/nanzhufeng_app_icon\"") &&
                manifest.contains("android:roundIcon=\"@mipmap/nanzhufeng_app_icon\""),
        )
        assertTrue("必须提供 Android 8+ 的自适应图标", adaptiveIcon.exists())
        assertTrue(
            "原始图标必须作为完整背景层呈现，不能被前景安全区缩放或裁切",
            adaptiveIcon.readText().contains("<background android:drawable=\"@drawable/nanzhufeng_app_icon\"") &&
                adaptiveIcon.readText().contains("<foreground android:drawable=\"@drawable/nanzhufeng_launcher_foreground\""),
        )
        assertTrue("必须提供桌面图标前景资源", foreground.exists())
        assertTrue(
            "前景层必须完全透明，不能重新缩放或包装原始图标",
            foreground.readText().contains("#00000000"),
        )
    }
}
