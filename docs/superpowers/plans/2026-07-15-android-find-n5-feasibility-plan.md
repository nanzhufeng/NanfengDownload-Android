# Android 本地下载可行性原型 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不先开发完整 UI 的前提下，证明 OPPO Find N5 可以本地完成 YouTube/抖音公开单视频解析、下载、MP4 音视频合并、公共目录写入和抖音登录态持久化。

**Architecture:** 在现有桌面项目下新增独立 `android/` 工程，使用 Kotlin/Compose 承载诊断界面，Chaquopy 仅承载 `yt-dlp` 解析，Kotlin 原生网络层负责下载，Media3 Transformer 负责 MP4 合并。所有验证结果通过结构化 `ProbeResult` 返回；任一核心链路失败即停止，不提前开发完整首页、历史和设置页。

**Tech Stack:** Android Gradle Plugin 8.5.2、Gradle 8.7、Kotlin 2.0.20、compileSdk/targetSdk 35、minSdk 24、Jetpack Compose、Chaquopy 17.0.0、Python 3.13、yt-dlp 2026.06.09、Media3 1.8.1、JUnit 4、AndroidX Test。

## Global Constraints

- 主仓库目录为 `D:\CodexProjects\江湖工具箱\CleanVideoDownloader`，Android 工程固定放在 `android/`；Windows 下的 Android 编译和测试必须从纯英文 Git worktree 执行，当前路径为 `D:\CodexProjects\CleanVideoDownloader-AndroidProbe`。
- 包名固定为 `com.nanzhufeng.videodownloader`，应用显示名固定为 `南烛枫视频下载器`。
- 本阶段只验证公开单视频，不实现作者/频道批量读取、历史页、设置页和最终视觉稿。
- 单视频链接不得扩展为整个作者或频道。
- 不保存账号密码，不绕过会员、付费、DRM 或私密内容限制。
- 不使用 Playwright，不复制 Windows 路径和浏览器启动逻辑。
- MP4 合并优先使用 Media3；本阶段不安装 NDK、不打包 FFmpeg。
- Media3 固定为 `1.8.1`：`1.10.1` 的 AAR 要求 compileSdk 36，与本阶段 Android 35 基线冲突；待核心链路通过后再评估升级。
- 模拟器验证只能证明构建和基本链路；Find N5 真机验证是通过关卡的必要条件。
- 下载结果必须存在、非空、包含可识别媒体容器，并可由系统播放器打开。
- 每个任务完成后单独提交 Git；不得把 `build/`、APK 临时产物、Cookie 或测试下载文件提交。

---

## 当前环境事实

- Android Studio：`C:\Program Files\Android\Android Studio`
- JBR/JDK：`C:\Program Files\Android\Android Studio\jbr`，Java 21.0.10
- Android SDK：`C:\Users\Administrator\AppData\Local\Android\Sdk`
- 已安装平台：Android 35
- 已安装 Build Tools：34.0.0、35.0.0
- 已缓存 Gradle：8.7
- Python：`C:\Users\Administrator\AppData\Local\Programs\Python\Python313\python.exe`，3.13.3
- 当前已连接设备：`emulator-5554`，x86_64 模拟器
- 当前未确认：OPPO Find N5 ADB 连接

每次命令行构建前运行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

---

### Task 1: 建立可编译的 Android/Chaquopy 工程

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/proguard-rules.pro`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/MainActivity.kt`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/themes.xml`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: 本机 Android 35 SDK、Android Studio JBR、Python 3.13。
- Produces: 可通过 `android/gradlew.bat :app:assembleDebug` 构建的基础 APK；后续任务统一使用 `com.nanzhufeng.videodownloader` 包。

- [ ] **Step 1: 记录构建前失败基线**

Run:

```powershell
Test-Path .\android\gradlew.bat
```

Expected: `False`。

- [ ] **Step 2: 创建 Gradle 配置**

`android/settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NanzhufengVideoDownloader"
include(":app")
```

`android/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.chaquo.python") version "17.0.0" apply false
}
```

`android/gradle.properties`：

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

`android/app/build.gradle.kts`：

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.nanzhufeng.videodownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nanzhufeng.videodownloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-probe"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        buildPython("C:/Users/Administrator/AppData/Local/Programs/Python/Python313/python.exe")
        pip {
            install("yt-dlp==2026.6.9")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.media3:media3-common:1.8.1")
    implementation("androidx.media3:media3-transformer:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 3: 创建最小应用入口**

`android/app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name="com.chaquo.python.android.PyApplication"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:theme="@style/Theme.NanzhufengVideoDownloader"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`android/app/src/main/res/values/strings.xml`：

```xml
<resources>
    <string name="app_name">南烛枫视频下载器</string>
</resources>
```

`android/app/src/main/res/values/themes.xml`：

```xml
<resources>
    <style name="Theme.NanzhufengVideoDownloader" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">#1769FF</item>
        <item name="android:navigationBarColor">#F5F7FC</item>
        <item name="android:statusBarColor">#F5F7FC</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```

`android/app/src/main/java/com/nanzhufeng/videodownloader/MainActivity.kt`：

```kotlin
package com.nanzhufeng.videodownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("南烛枫 Android 可行性验证")
            }
        }
    }
}
```

`android/app/proguard-rules.pro` 保持空文件。

- [ ] **Step 4: 扩展忽略规则**

在根 `.gitignore` 追加：

```gitignore
android/.gradle/
android/local.properties
android/**/build/
android/captures/
android/.idea/
*.apk
*.aab
```

- [ ] **Step 5: 生成 Gradle Wrapper**

Run:

```powershell
$gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.7-bin" -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
Push-Location .\android
& $gradle wrapper --gradle-version 8.7
Pop-Location
```

Expected: 生成 `android/gradlew.bat` 和 `android/gradle/wrapper/gradle-wrapper.properties`。

- [ ] **Step 6: 构建并安装最小 APK**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:assembleDebug
.\android\gradlew.bat -p .\android :app:installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.nanzhufeng.videodownloader/.MainActivity
```

Expected: `BUILD SUCCESSFUL`，模拟器显示“南烛枫 Android 可行性验证”。

- [ ] **Step 7: Commit**

```powershell
git add .gitignore android
git commit -m "build(android): 建立可行性验证工程"
```

---

### Task 2: 定义探测结果与链接分类契约

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeModels.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/UrlClassifier.kt`
- Test: `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/UrlClassifierTest.kt`

**Interfaces:**
- Consumes: 用户输入的分享文案或 URL。
- Produces: `UrlClassifier.extractAndClassify(text): ClassifiedSource`；后续 YouTube 和抖音探测只接受分类后的单视频 URL。

- [ ] **Step 1: 写失败测试**

`UrlClassifierTest.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlClassifierTest {
    @Test
    fun youtubeWatchIsSingleVideo() {
        val source = UrlClassifier.extractAndClassify("https://www.youtube.com/watch?v=abcdefghijk")
        assertEquals(Platform.YOUTUBE, source.platform)
        assertEquals(SourceKind.SINGLE_VIDEO, source.kind)
    }

    @Test
    fun douyinShareTextExtractsShortUrl() {
        val source = UrlClassifier.extractAndClassify(
            "复制打开抖音，看看TA的更多作品。 https://v.douyin.com/AbCdEfGh/"
        )
        assertEquals(Platform.DOUYIN, source.platform)
        assertEquals(SourceKind.UNKNOWN_DOUYIN_SHARE, source.kind)
        assertEquals("https://v.douyin.com/AbCdEfGh/", source.url)
    }

    @Test
    fun youtubeChannelIsNotSingleVideo() {
        val source = UrlClassifier.extractAndClassify("https://www.youtube.com/@example/videos")
        assertEquals(SourceKind.CHANNEL_OR_PLAYLIST, source.kind)
    }

    @Test
    fun missingUrlFails() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlClassifier.extractAndClassify("只有普通文字")
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.UrlClassifierTest"
```

Expected: FAIL，提示 `UrlClassifier`、`Platform` 或 `SourceKind` 未定义。

- [ ] **Step 3: 实现模型与分类器**

`ProbeModels.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

enum class Platform { YOUTUBE, DOUYIN }

enum class SourceKind {
    SINGLE_VIDEO,
    CHANNEL_OR_PLAYLIST,
    UNKNOWN_DOUYIN_SHARE,
}

data class ClassifiedSource(
    val platform: Platform,
    val kind: SourceKind,
    val url: String,
)

data class ProbeResult(
    val success: Boolean,
    val stage: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)
```

`UrlClassifier.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

object UrlClassifier {
    private val urlRegex = Regex("https?://[^\\s]+")

    fun extractAndClassify(text: String): ClassifiedSource {
        val raw = urlRegex.find(text)?.value
            ?.trimEnd('。', '，', ',', '.', ')', '）', ']', '】')
            ?: throw IllegalArgumentException("没有找到抖音或 YouTube 链接")
        val lower = raw.lowercase()

        return when {
            "youtube.com/watch" in lower || "youtu.be/" in lower ->
                ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, raw)
            "youtube.com/playlist" in lower || "youtube.com/@" in lower ||
                "youtube.com/channel/" in lower || "youtube.com/c/" in lower ->
                ClassifiedSource(Platform.YOUTUBE, SourceKind.CHANNEL_OR_PLAYLIST, raw)
            "douyin.com/video/" in lower ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.SINGLE_VIDEO, raw)
            "douyin.com/user/" in lower ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.CHANNEL_OR_PLAYLIST, raw)
            "v.douyin.com/" in lower ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, raw)
            else -> throw IllegalArgumentException("只支持抖音和 YouTube 链接")
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.UrlClassifierTest"
```

Expected: 4 tests PASS。

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/nanzhufeng/videodownloader/probe android/app/src/test
git commit -m "test(android): 固化单视频链接分类规则"
```

---

### Task 3: 验证 Chaquopy 与 yt-dlp 单视频解析

**Files:**
- Create: `android/app/src/main/python/nanzhufeng_probe/__init__.py`
- Create: `android/app/src/main/python/nanzhufeng_probe/youtube_probe.py`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YoutubeProbe.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/probe/PythonRuntimeInstrumentedTest.kt`

**Interfaces:**
- Consumes: `ClassifiedSource`，且 `kind` 必须为 `SINGLE_VIDEO`。
- Produces: `YoutubeProbe.extractSingle(url): YoutubeMediaInfo`，包含标题、作者、视频直链、可选音频直链和 HTTP headers。

- [ ] **Step 1: 写运行时失败测试**

`PythonRuntimeInstrumentedTest.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PythonRuntimeInstrumentedTest {
    @Test
    fun embeddedPythonLoadsPinnedYtDlp() {
        val info = YoutubeProbe().runtimeInfo()
        assertTrue(info.python.startsWith("3.13"))
        assertEquals("2026.06.09", info.ytDlp)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.probe.PythonRuntimeInstrumentedTest"
```

Expected: FAIL，提示 `YoutubeProbe` 未定义。

- [ ] **Step 3: 实现 Python 单视频解析**

`nanzhufeng_probe/__init__.py` 为空文件。

`youtube_probe.py`：

```python
import json
import sys

import yt_dlp
from yt_dlp import YoutubeDL


def runtime_info() -> str:
    return json.dumps(
        {"python": sys.version.split()[0], "yt_dlp": yt_dlp.version.__version__},
        ensure_ascii=False,
    )


def _best(items, predicate, score):
    candidates = [item for item in items if predicate(item)]
    return max(candidates, key=score) if candidates else None


def extract_single(url: str) -> str:
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 1,
    }
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)

    if info.get("_type") in {"playlist", "multi_video"} or info.get("entries"):
        raise ValueError("单视频探测返回了列表，已中止")

    formats = info.get("formats") or []
    progressive = _best(
        formats,
        lambda f: f.get("url") and f.get("ext") == "mp4"
        and f.get("vcodec") not in {None, "none"}
        and f.get("acodec") not in {None, "none"}
        and (f.get("height") or 0) <= 720,
        lambda f: ((f.get("height") or 0), (f.get("tbr") or 0)),
    )
    video = _best(
        formats,
        lambda f: f.get("url") and f.get("ext") == "mp4"
        and f.get("vcodec") not in {None, "none"}
        and f.get("acodec") in {None, "none"},
        lambda f: ((f.get("height") or 0), (f.get("tbr") or 0)),
    )
    audio = _best(
        formats,
        lambda f: f.get("url") and f.get("acodec") not in {None, "none"}
        and f.get("vcodec") in {None, "none"},
        lambda f: (f.get("abr") or f.get("tbr") or 0),
    )

    chosen_video = progressive or video
    if not chosen_video:
        raise ValueError("没有找到可下载的 MP4 视频流")

    result = {
        "id": str(info.get("id") or ""),
        "title": str(info.get("title") or "未知标题"),
        "creator": str(info.get("channel") or info.get("uploader") or "未知作者"),
        "video_url": chosen_video["url"],
        "audio_url": "" if progressive else (audio or {}).get("url", ""),
        "video_ext": chosen_video.get("ext") or "mp4",
        "audio_ext": (audio or {}).get("ext") or "m4a",
        "headers": info.get("http_headers") or {},
    }
    return json.dumps(result, ensure_ascii=False)
```

- [ ] **Step 4: 实现 Kotlin 桥接**

`YoutubeProbe.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

import com.chaquo.python.Python
import org.json.JSONObject

data class RuntimeInfo(val python: String, val ytDlp: String)

data class YoutubeMediaInfo(
    val id: String,
    val title: String,
    val creator: String,
    val videoUrl: String,
    val audioUrl: String?,
    val headers: Map<String, String>,
)

class YoutubeProbe {
    private val module by lazy {
        Python.getInstance().getModule("nanzhufeng_probe.youtube_probe")
    }

    fun runtimeInfo(): RuntimeInfo {
        val json = JSONObject(module.callAttr("runtime_info").toString())
        return RuntimeInfo(json.getString("python"), json.getString("yt_dlp"))
    }

    fun extractSingle(url: String): YoutubeMediaInfo {
        val json = JSONObject(module.callAttr("extract_single", url).toString())
        val headersJson = json.getJSONObject("headers")
        val headers = headersJson.keys().asSequence().associateWith(headersJson::getString)
        return YoutubeMediaInfo(
            id = json.getString("id"),
            title = json.getString("title"),
            creator = json.getString("creator"),
            videoUrl = json.getString("video_url"),
            audioUrl = json.getString("audio_url").ifBlank { null },
            headers = headers,
        )
    }
}
```

- [ ] **Step 5: 运行运行时测试**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.probe.PythonRuntimeInstrumentedTest"
```

Expected: PASS，日志无 `ModuleNotFoundError` 或 ABI 加载错误。

- [ ] **Step 6: Commit**

```powershell
git add android/app/src/main/python android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YoutubeProbe.kt android/app/src/androidTest
git commit -m "feat(android): 验证嵌入式 yt-dlp 运行时"
```

---

### Task 4: 实现可恢复、可取消的直链下载探测器

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/HttpFileDownloader.kt`
- Test: `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/MediaFileValidatorTest.kt`

**Interfaces:**
- Consumes: `YoutubeMediaInfo.videoUrl/audioUrl/headers` 或抖音捕获的媒体 URL。
- Produces: `HttpFileDownloader.download(request): File`；已有 `.part` 且服务端支持 Range 时续传；取消时抛出 `CancellationException`。

- [ ] **Step 1: 写媒体校验失败测试**

```kotlin
package com.nanzhufeng.videodownloader.probe

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileValidatorTest {
    @Test
    fun rejectsTinyHtmlResponse() {
        val file = File.createTempFile("probe", ".mp4")
        file.writeText("<html>blocked</html>")
        assertFalse(MediaFileValidator.isLikelyMedia(file))
        file.delete()
    }

    @Test
    fun acceptsMp4FtypHeaderAboveMinimumSize() {
        val file = File.createTempFile("probe", ".mp4")
        file.outputStream().use { out ->
            out.write(byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()))
            out.write(ByteArray(70 * 1024))
        }
        assertTrue(MediaFileValidator.isLikelyMedia(file))
        file.delete()
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.MediaFileValidatorTest"
```

Expected: FAIL，`MediaFileValidator` 未定义。

- [ ] **Step 3: 实现下载与校验**

`HttpFileDownloader.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class DirectDownloadRequest(
    val url: String,
    val headers: Map<String, String>,
    val target: File,
)

class HttpFileDownloader {
    fun download(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File {
        val partial = File(request.target.parentFile, request.target.name + ".part")
        partial.parentFile?.mkdirs()
        val existing = partial.takeIf(File::exists)?.length() ?: 0L
        val connection = URL(request.url).openConnection() as HttpURLConnection
        request.headers.forEach(connection::setRequestProperty)
        if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true

        try {
            val append = existing > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            val start = if (append) existing else 0L
            if (!append && partial.exists()) partial.delete()
            val bodyLength = connection.contentLengthLong.coerceAtLeast(0L)
            val total = if (bodyLength > 0L) start + bodyLength else 0L
            var downloaded = start

            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (cancelled.get()) throw CancellationException("下载已取消")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }

            if (!partial.renameTo(request.target)) {
                partial.copyTo(request.target, overwrite = true)
                partial.delete()
            }
            return request.target
        } finally {
            connection.disconnect()
        }
    }
}

object MediaFileValidator {
    fun isLikelyMedia(file: File): Boolean {
        if (!file.isFile || file.length() < 64 * 1024) return false
        val header = ByteArray(64)
        val count = file.inputStream().use { it.read(header) }
        if (count <= 0) return false
        val text = header.copyOf(count).toString(Charsets.ISO_8859_1)
        return "ftyp" in text || "webm" in text || text.startsWith("ID3")
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.MediaFileValidatorTest"
```

Expected: 2 tests PASS。

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/nanzhufeng/videodownloader/probe/HttpFileDownloader.kt android/app/src/test
git commit -m "feat(android): 添加直链下载与媒体校验"
```

---

### Task 5: 用 Media3 合并独立视频与音频

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/Media3MuxProbe.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/probe/Media3MuxProbeInstrumentedTest.kt`

**Interfaces:**
- Consumes: 一个视频文件和一个音频文件。
- Produces: `Media3MuxProbe.merge(video, audio, output): File`；成功输出 MP4，取消时停止 Transformer。

- [ ] **Step 1: 写失败测试壳**

```kotlin
package com.nanzhufeng.videodownloader.probe

import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3MuxProbeInstrumentedTest {
    @Test
    fun compositionDeclaresOneVideoAndOneAudioTrack() {
        val trackTypes = Media3MuxProbe.declaredTrackTypes()
        assertEquals(setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO), trackTypes)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.probe.Media3MuxProbeInstrumentedTest"
```

Expected: FAIL，`Media3MuxProbe` 未定义。

- [ ] **Step 3: 实现 Media3 合并器**

`Media3MuxProbe.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object Media3MuxProbe {
    fun declaredTrackTypes(): Set<Int> = setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)

    suspend fun merge(context: Context, video: File, audio: File, output: File): File =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                output.parentFile?.mkdirs()
                output.delete()

                val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(video))).build()
                val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audio))).build()
                val videoSequence = EditedMediaItemSequence.withVideoFrom(listOf(videoItem))
                val audioSequence = EditedMediaItemSequence.withAudioFrom(listOf(audioItem))
                val composition = Composition.Builder(videoSequence, audioSequence)
                    .setTransmuxVideo(true)
                    .setTransmuxAudio(true)
                    .build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (continuation.isActive) continuation.resume(output)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }
                    })
                    .build()

                continuation.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, output.absolutePath)
            }
        }
}
```

- [ ] **Step 4: 运行声明测试**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.probe.Media3MuxProbeInstrumentedTest"
```

Expected: PASS。

- [ ] **Step 5: 用 Task 3/4 的真实 YouTube 双流做合并验证**

在诊断界面输入当前可访问的公开 YouTube 单视频；若 `audioUrl != null`，分别下载视频和音频到缓存，再调用 `merge`。验收必须同时满足：

```text
输出扩展名：.mp4
输出大小：大于 64 KB
MediaFileValidator：true
系统播放器：有画面且有声音
```

若 Media3 在模拟器或 Find N5 任一环境无法合并该双流，记录 `ExportException.errorCode` 与输入容器/编码后停止本计划，不转入 FFmpeg 临时绕过。

- [ ] **Step 6: Commit**

```powershell
git add android/app/build.gradle.kts android/app/src/main/java/com/nanzhufeng/videodownloader/probe/Media3MuxProbe.kt android/app/src/androidTest
git commit -m "feat(android): 验证 Media3 音视频合并"
```

---

### Task 6: 验证 MediaStore 公共目录写入

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/MediaStoreProbe.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/probe/MediaStoreProbeInstrumentedTest.kt`

**Interfaces:**
- Consumes: 缓存中的已验证 MP4。
- Produces: `content://` URI，目标目录固定为 `Movies/南烛枫视频下载器/Probe/`。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.nanzhufeng.videodownloader.probe

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreProbeInstrumentedTest {
    @Test
    fun writesProbeFileToSharedMovies() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val input = File(context.cacheDir, "storage-probe.mp4")
        input.writeBytes(ByteArray(70 * 1024) { index -> (index % 251).toByte() })
        val uri = MediaStoreProbe.writeVideo(context, input, "storage-probe.mp4")
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")!!.length
        assertTrue(size >= input.length())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.probe.MediaStoreProbeInstrumentedTest"
```

Expected: FAIL，`MediaStoreProbe` 未定义。

- [ ] **Step 3: 实现 MediaStore 写入**

```kotlin
package com.nanzhufeng.videodownloader.probe

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MediaStoreProbe {
    fun writeVideo(context: Context, source: File, displayName: String): Uri {
        require(Build.VERSION.SDK_INT >= 29) { "公共 Movies 目录探测要求 Android 10 或更高版本" }
        require(source.isFile && source.length() > 0L) { "源文件不存在或为空" }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/南烛枫视频下载器/Probe",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ) { "MediaStore 创建失败" }
        try {
            resolver.openOutputStream(uri, "w")!!.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.probe.MediaStoreProbeInstrumentedTest"
```

Expected: PASS；模拟器文件管理器可看到 `Movies/南烛枫视频下载器/Probe/storage-probe.mp4`。

- [ ] **Step 5: Commit**

```powershell
git add android/app/src/main/java/com/nanzhufeng/videodownloader/probe/MediaStoreProbe.kt android/app/src/androidTest
git commit -m "feat(android): 验证公共视频目录写入"
```

---

### Task 7: 验证抖音 WebView 登录态和单视频流捕获

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/DouyinCaptureStore.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/DouyinProbeActivity.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Test: `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/DouyinCaptureStoreTest.kt`

**Interfaces:**
- Consumes: 当前有效的抖音单作品分享链接。
- Produces: `DouyinCaptureStore.latestMediaUrl` 和持久 WebView Cookie；不输出账号密码。

- [ ] **Step 1: 写媒体 URL 过滤失败测试**

```kotlin
package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinCaptureStoreTest {
    @Test
    fun capturesMediaOnlyFromTargetWorkPage() {
        DouyinCaptureStore.begin("https://www.douyin.com/video/7315041660419853608")
        DouyinCaptureStore.capture(
            pageUrl = "https://www.douyin.com/video/7315041660419853608",
            requestUrl = "https://example.com/video/tos/cn/tos-cn-ve-15/o123.mp4",
        )
        assertTrue(DouyinCaptureStore.latestMediaUrl?.endsWith("o123.mp4") == true)
    }

    @Test
    fun rejectsMediaFromAnotherWorkPage() {
        DouyinCaptureStore.begin("https://www.douyin.com/video/7315041660419853608")
        DouyinCaptureStore.capture(
            pageUrl = "https://www.douyin.com/video/9999999999999999999",
            requestUrl = "https://example.com/video/tos/cn/tos-cn-ve-15/other.mp4",
        )
        assertFalse(DouyinCaptureStore.latestMediaUrl != null)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.DouyinCaptureStoreTest"
```

Expected: FAIL，`DouyinCaptureStore` 未定义。

- [ ] **Step 3: 实现线程安全捕获存储**

```kotlin
package com.nanzhufeng.videodownloader.probe

import java.util.concurrent.atomic.AtomicReference

object DouyinCaptureStore {
    private val media = AtomicReference<String?>(null)
    private val targetWorkId = AtomicReference<String?>(null)
    private val workIdPattern = Regex("/video/(\\d+)")

    val latestMediaUrl: String? get() = media.get()

    fun begin(sourceUrl: String) {
        media.set(null)
        targetWorkId.set(extractWorkId(sourceUrl))
    }

    fun updatePage(pageUrl: String) {
        val pageWorkId = extractWorkId(pageUrl) ?: return
        targetWorkId.compareAndSet(null, pageWorkId)
    }

    fun capture(pageUrl: String, requestUrl: String) {
        val expected = targetWorkId.get() ?: return
        val pageWorkId = extractWorkId(pageUrl) ?: return
        if (pageWorkId == expected && isMediaUrl(requestUrl)) {
            media.compareAndSet(null, requestUrl)
        }
    }

    private fun extractWorkId(url: String): String? =
        workIdPattern.find(url)?.groupValues?.get(1)

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        val mediaPath = "/video/tos/" in lower || "/play/" in lower || ".mp4" in lower
        val image = lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".png")
        return lower.startsWith("https://") && mediaPath && !image && "douyin.com/video/" !in lower
    }
}
```

- [ ] **Step 4: 实现可见 WebView 探测页**

`DouyinProbeActivity.kt`：

```kotlin
package com.nanzhufeng.videodownloader.probe

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class DouyinProbeActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = requireNotNull(intent.getStringExtra(EXTRA_URL))
        DouyinCaptureStore.begin(url)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            flush()
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    DouyinCaptureStore.updatePage(url)
                    super.onPageFinished(view, url)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    DouyinCaptureStore.capture(
                        pageUrl = view.url.orEmpty(),
                        requestUrl = request.url.toString(),
                    )
                    return super.shouldInterceptRequest(view, request)
                }
            }
            loadUrl(url)
        }
        setContentView(webView)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "source_url"
    }
}
```

在 Manifest 的 `<application>` 内增加：

```xml
<activity
    android:name=".probe.DouyinProbeActivity"
    android:exported="false" />
```

- [ ] **Step 5: 运行单元测试**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:testDebugUnitTest --tests "*.DouyinCaptureStoreTest"
```

Expected: 2 tests PASS。

- [ ] **Step 6: 手动验证登录态和公开单视频**

1. 从诊断页打开 `https://www.douyin.com/`，完成登录。
2. 返回诊断页并强制结束应用进程。
3. 重启应用，再次打开抖音页面，确认账号仍处于登录状态。
4. 打开当前有效的抖音单视频分享链接并播放一次。
5. 返回诊断页，确认 `latestMediaUrl` 非空。
6. 使用 Task 4 下载该 URL，并由 `MediaFileValidator` 验证。

Expected: 登录态跨重启保留；捕获到的流可下载为非空媒体。若只能捕获推荐流、广告流或其他作者内容，则本任务失败，必须增加作品 ID 关联后重新验证，不能继续完整应用开发。

- [ ] **Step 7: Commit**

```powershell
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/nanzhufeng/videodownloader/probe android/app/src/test
git commit -m "feat(android): 验证抖音登录态与媒体捕获"
```

---

### Task 8: 集成诊断界面并执行端到端关卡

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeViewModel.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeScreen.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/MainActivity.kt`
- Create: `docs/verification/android-find-n5-feasibility-result.md`

**Interfaces:**
- Consumes: Tasks 2-7 的分类、解析、下载、合并、存储和抖音捕获接口。
- Produces: 一个仅用于验证的诊断 APK，以及包含真实设备证据的结论文件。

- [ ] **Step 1: 实现诊断状态机**

`ProbeViewModel.kt` 只允许以下状态：

```kotlin
package com.nanzhufeng.videodownloader.probe

sealed interface ProbeUiState {
    data object Idle : ProbeUiState
    data class Running(val stage: String) : ProbeUiState
    data class Passed(val message: String) : ProbeUiState
    data class Failed(val stage: String, val message: String) : ProbeUiState
}
```

ViewModel 的每次探测都在 `viewModelScope.launch(Dispatchers.IO)` 中执行，并在异常时转为 `Failed`；禁止空 `catch`，错误信息必须包含阶段名和异常类型。

- [ ] **Step 2: 实现最小诊断界面**

`ProbeScreen.kt` 必须包含：

```text
多行分享文本输入框
检查 Python/yt-dlp
解析 YouTube 单视频
下载并合并 YouTube
打开抖音登录/探测页
下载捕获的抖音流
写入 Movies 公共目录
当前阶段、结果、文件大小和输出 URI
```

界面不复刻最终首页，不加入历史和设置导航。`MainActivity` 只调用 `ProbeScreen()`。

- [ ] **Step 3: 运行完整自动验证**

Run:

```powershell
.\android\gradlew.bat -p .\android clean testDebugUnitTest connectedDebugAndroidTest assembleDebug
```

Expected: `BUILD SUCCESSFUL`；全部单元测试和仪器测试 PASS。

- [ ] **Step 4: 在模拟器运行公开 YouTube 链路**

必须记录：

```text
yt-dlp 版本
解析耗时
标题和作者
视频/音频是否分流
下载字节数
Media3 合并耗时
输出 MP4 大小
系统播放器是否有画面和声音
```

任一步失败，先保存 Logcat 和 Python 错误，不切换到作者/频道批量功能。

- [ ] **Step 5: 连接 OPPO Find N5**

用户在手机开启开发者选项和 USB 调试后运行：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
```

Expected: 除 `emulator-5554` 外出现一个状态为 `device` 的 OPPO 实体设备；若为 `unauthorized`，在手机确认 USB 调试授权后重试。

- [ ] **Step 6: 在 Find N5 执行真机关卡**

Run:

```powershell
.\android\gradlew.bat -p .\android :app:installDebug
```

在 Find N5 上逐项验证：

```text
Python 3.13 启动成功
yt-dlp 2026.06.09 加载成功
公开 YouTube 单视频只解析 1 项
YouTube 视频下载成功
独立音视频经 Media3 合并后可播放
公开抖音单视频只捕获目标作品
抖音登录状态重启后保留
输出写入 Movies/南烛枫视频下载器/Probe
应用切到后台后当前探测不立即丢失
```

- [ ] **Step 7: 写真实验证结论**

`docs/verification/android-find-n5-feasibility-result.md` 必须只写实测结果，并按以下结构：

```markdown
# Android Find N5 可行性验证结果

日期：实际执行日期
设备：adb shell getprop ro.product.model 的实际输出
Android：adb shell getprop ro.build.version.release 的实际输出
APK Commit：git rev-parse --short HEAD 的实际输出

## 已通过

- 逐项列出有日志、文件或截图证据的结果。

## 未通过

- 逐项列出失败阶段和原始错误摘要；没有失败时写“无”。

## 产物

- APK 实际路径
- YouTube 输出 URI 与文件大小
- 抖音输出 URI 与文件大小
- Logcat 文件路径

## 结论

- 只能写“通过，可进入完整应用计划”或“不通过，停止完整应用开发”。
```

写文件时将所有说明文字替换为真实命令输出，不能保留示例句。

- [ ] **Step 8: 最终验证和 Commit**

Run:

```powershell
git diff --check
git status --short
.\android\gradlew.bat -p .\android testDebugUnitTest connectedDebugAndroidTest assembleDebug
```

Expected: 测试与构建成功；工作区只包含本任务预期文件。

```powershell
git add android docs/verification/android-find-n5-feasibility-result.md docs/superpowers/specs/2026-07-15-android-find-n5-design.md
git commit -m "test(android): 完成 Find N5 本地下载可行性验证"
```

---

## 通过标准与停止条件

只有以下条件全部满足，才允许编写下一份“完整 Android 应用实施计划”：

1. 模拟器自动测试与构建全部通过。
2. Find N5 上 Chaquopy/yt-dlp 正常加载。
3. Find N5 上 YouTube 公开单视频解析、下载、合并和播放成功。
4. Find N5 上抖音目标单作品捕获和下载成功，未混入其他作品。
5. 抖音登录状态重启后保留。
6. MediaStore 公共目录文件真实存在、非空且可播放。
7. 结果文档包含真实设备、Commit、URI、文件大小和日志证据。

遇到以下任一情况立即停止，不扩大 UI 或批量功能：

- Chaquopy 无法在 Find N5 ARM64/16 KB page size 环境稳定启动。
- 当前 yt-dlp 需要的脚本运行时无法在 APK 内满足。
- Media3 无法合并当前 YouTube 独立音视频流。
- 抖音 WebView 只能捕获推荐流，无法绑定目标作品 ID。
- 输出文件为空、不可播放或只能写入应用私有缓存。

停止后保留诊断 APK、Logcat 和结果文档，再根据证据选择桌面伴侣服务或私有后端方案；不在本计划中临时引入来源不明的 FFmpeg APK、第三方解析 API或绕过机制。
