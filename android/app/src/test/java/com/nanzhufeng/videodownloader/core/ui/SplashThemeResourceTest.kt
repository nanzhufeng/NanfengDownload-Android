package com.nanzhufeng.videodownloader.core.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashThemeResourceTest {
    @Test
    fun android12SplashTheme_hasNoWhiteIconBacking() {
        val themeFile = File("src/main/res/values/themes.xml")

        assertTrue("启动主题必须存在", themeFile.exists())
        assertTrue(
            "启动主题必须采用不带图标底板的 SplashScreen 变体",
            themeFile.readText().contains(
                "name=\"Theme.NanzhufengVideoDownloader.Starting\" parent=\"Theme.SplashScreen\"",
            ),
        )
        assertTrue(
            "启动图标必须使用留足安全边距的图标本体",
            themeFile.readText().contains("<item name=\"windowSplashScreenAnimatedIcon\">@drawable/nanzhufeng_splash_icon</item>"),
        )
        val splashIcon = File("src/main/res/drawable/nanzhufeng_splash_icon.xml")
        assertTrue("启动图标需要单独的安全边距资源", splashIcon.exists())
        assertTrue(
            "启动图标安全边距必须继续直接引用原始图标，不得重新加白底",
            splashIcon.readText().contains("android:drawable=\"@drawable/nanzhufeng_app_icon\""),
        )
        assertTrue(
            "入口 Activity 必须使用启动主题",
            File("src/main/AndroidManifest.xml").readText().contains(
                "android:theme=\"@style/Theme.NanzhufengVideoDownloader.Starting\"",
            ),
        )
    }
}
