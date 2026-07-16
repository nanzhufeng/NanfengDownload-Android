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
            "自适应图标背景必须透明，不能使用白色底板",
            adaptiveIcon.readText().contains("@color/nanzhufeng_launcher_icon_transparent"),
        )
        assertTrue("图标前景必须保留安全边距", foreground.exists())
        assertTrue(
            "图标前景必须直接引用真实图标本体",
            foreground.readText().contains("@drawable/nanzhufeng_app_icon"),
        )
    }
}
