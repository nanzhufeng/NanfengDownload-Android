package com.nanzhufeng.videodownloader.core.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourceTest {
    @Test
    fun launcherIcon_usesSourceColoredAdaptiveLayersInsteadOfLegacyWhitePlate() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val adaptiveIcon = File("src/main/res/mipmap-anydpi-v26/nanzhufeng_app_icon.xml")
        val background = File("src/main/res/drawable/nanzhufeng_launcher_background.xml")
        val foreground = File("src/main/res/drawable/nanzhufeng_launcher_foreground.xml")

        assertTrue(
            "桌面图标必须使用自适应图标资源，避免系统给普通 PNG 套白色圆底",
            manifest.contains("android:icon=\"@mipmap/nanzhufeng_app_icon\"") &&
                manifest.contains("android:roundIcon=\"@mipmap/nanzhufeng_app_icon\""),
        )
        assertTrue("必须提供 Android 8+ 的自适应图标", adaptiveIcon.exists())
        assertTrue(
            "自适应图标必须使用本体同色背景承接安全区，不能让系统补黑白底",
            adaptiveIcon.readText().contains("<background android:drawable=\"@drawable/nanzhufeng_launcher_background\"") &&
                adaptiveIcon.readText().contains("<foreground android:drawable=\"@drawable/nanzhufeng_launcher_foreground\""),
        )
        assertTrue("必须提供从原图采样的深蓝渐变背景", background.exists())
        assertTrue("必须提供桌面图标前景资源", foreground.exists())
        assertTrue(
            "16dp 已确认补偿必须保留主体比例、四周留白和完整印章",
            foreground.readText().contains("<inset") &&
                foreground.readText().contains("@drawable/nanzhufeng_launcher_foreground_art") &&
                !foreground.readText().contains("@drawable/nanzhufeng_app_icon") &&
                foreground.readText().contains("android:inset=\"16dp\""),
        )
    }
}
