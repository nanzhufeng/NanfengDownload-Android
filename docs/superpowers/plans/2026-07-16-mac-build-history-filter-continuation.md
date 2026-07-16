# Mac 构建与外屏历史筛选续接 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从迁移提交 `8837603` 继续，在不改变业务流程的前提下让 Android 工程在当前 Mac 可维护地构建并通过 JVM 测试，再让 380dp 外屏历史筛选无需横向滚动即可完整显示。

**Architecture:** Chaquopy 的目标 Python 版本继续固定为 3.13，但构建解释器路径改为本机私有配置或环境变量，仓库不提交机器绝对路径。历史筛选继续由 `HistoryScreen` 单点拥有，只把横向滚动行替换为可换行布局，不改变筛选状态、Repository 或数据库。

**Tech Stack:** Gradle 8.7、Android Gradle Plugin 8.5.2、Kotlin、Jetpack Compose、Chaquopy、JUnit4、AndroidX Compose UI Test。

## Global Constraints

- 只支持公开内容和登录账号本来有权限访问的内容；不实现会员、付费、DRM 或私密内容绕过。
- 不卸载 OPPO 上的应用、不清空应用数据、不重置设备；本计划不操作 OPPO。
- 不把 Python、SDK、Cookie、登录态或本机绝对路径提交到仓库。
- 保持 `version = "3.13"` 与 `yt-dlp==2026.6.9` 不变。
- 保持首页、下载、历史和设置的数据所有者不变；本计划不修改下载业务语义。
- 先证明失败，再进行最小修改；构建、自动测试、模拟器、真机结果分别报告。

---

## 文件边界

- Modify: `android/app/build.gradle.kts` — 删除 Windows 绝对路径，读取可选的 Gradle 属性或环境变量。
- Local-only: `android/local.properties` — 保存当前 Mac 的 SDK 与 Python 3.13 路径，已被 `.gitignore` 排除。
- Modify: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenInstrumentedTest.kt` — 固化 380dp 下三个筛选组均可见的契约。
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt` — 用换行布局替代横向滚动。
- Modify: `PROJECT_HANDOFF.md` — 记录实际构建和模拟器证据；若文件不存在则创建。

### Task 1: 让 Chaquopy 构建配置跨 Windows 与 Mac 可维护

**Files:**
- Modify: `android/app/build.gradle.kts`
- Local-only: `android/local.properties`
- Test: `android/app/build.gradle.kts` 的 Gradle 配置与 `:app:testDebugUnitTest`

**Interfaces:**
- Consumes: `nanzhufeng.buildPython` Gradle 属性或 `NANZHUFENG_BUILD_PYTHON` 环境变量。
- Produces: Chaquopy `buildPython(...)` 的可选本机路径；未配置时让 Chaquopy 按标准名称发现解释器。

- [ ] **Step 1: 记录当前失败基线**

Run:

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

Expected: FAIL，错误直接指向 Windows `C:/Users/.../Python313/python.exe` 不存在，或 Python 3.13 构建解释器不可用；不得把 Java PATH 问题误记为项目代码失败。

- [ ] **Step 2: 改为可选本机配置**

在 `android/app/build.gradle.kts` 顶部加入：

```kotlin
val configuredBuildPython = providers.gradleProperty("nanzhufeng.buildPython")
    .orElse(providers.environmentVariable("NANZHUFENG_BUILD_PYTHON"))
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
```

将 Chaquopy 配置改为：

```kotlin
chaquopy {
    defaultConfig {
        version = "3.13"
        configuredBuildPython?.let { buildPython(it) }
        pip {
            install("yt-dlp==2026.6.9")
        }
    }
}
```

- [ ] **Step 3: 配置当前 Mac 的私有路径**

在不提交的 `android/local.properties` 中保留 `sdk.dir`；Python 路径通过命令环境传入：

```bash
export NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13"
```

Expected: `git status --short` 不出现 `android/local.properties`，且 `build.gradle.kts` 不包含 `/Users/`、`C:/Users/` 或账号信息。

- [ ] **Step 4: 运行 JVM 测试确认通过**

Run:

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`，且全部现有 JVM 测试通过。

- [ ] **Step 5: 提交 Mac 构建适配**

```bash
git add android/app/build.gradle.kts
git commit -m "build(android): make Chaquopy Python path portable"
```

### Task 2: 创建隔离的 Find N5 近似模拟器

**Files:**
- Local-only: `~/.android/avd/NanzhufengFindN5Api35.avd/`
- Test: Android SDK `emulator` 与 `adb`

**Interfaces:**
- Consumes: 已安装的 `system-images;android-35;google_apis;arm64-v8a` 和设备模板 `84`（7.6 英寸折叠屏）。
- Produces: 序列号固定通过运行时探测获得的隔离模拟器；仪器测试命令必须设置 `ANDROID_SERIAL`，不得误装到 OPPO。

- [ ] **Step 1: 创建 AVD**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/avdmanager \
create avd --force \
  --name NanzhufengFindN5Api35 \
  --package 'system-images;android-35;google_apis;arm64-v8a' \
  --device 84
```

Expected: AVD 创建成功，未连接、安装或修改 OPPO。

- [ ] **Step 2: 启动并等待模拟器完成开机**

Run:

```bash
/Users/nanzhufeng/Library/Android/sdk/emulator/emulator \
  -avd NanzhufengFindN5Api35 -no-snapshot -no-boot-anim
```

在另一终端运行：

```bash
ANDROID_SERIAL=emulator-5554 \
/Users/nanzhufeng/Library/Android/sdk/platform-tools/adb wait-for-device
```

Expected: `adb -s emulator-5554 shell getprop sys.boot_completed` 返回 `1`。若序列号不是 `emulator-5554`，只用 `adb devices -l` 中以 `emulator-` 开头的实际序列号替换，不使用 OPPO 序列号。

### Task 3: 固化 380dp 外屏筛选可见性契约

**Files:**
- Modify: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenInstrumentedTest.kt`
- Test: `HistoryScreenInstrumentedTest.outerScreen_keepsAllFilterGroupsVisibleWithoutHorizontalScrolling`

**Interfaces:**
- Consumes: `HistoryScreen(history, onRetry, onDeleteRecord)`。
- Produces: 380dp 宽度下“全部状态”“全部平台”“近 30 天”三个筛选组代表项同时可见的回归契约。

- [ ] **Step 1: 扩充失败测试**

在现有测试末尾加入：

```kotlin
composeRule.onNodeWithText("全部状态").assertIsDisplayed()
composeRule.onNodeWithText("全部平台").assertIsDisplayed()
composeRule.onNodeWithText("近 30 天").assertIsDisplayed()
```

并为筛选容器增加测试标签断言：

```kotlin
composeRule.onNodeWithTag("history-filters").assertIsDisplayed()
```

- [ ] **Step 2: 在指定模拟器上验证测试失败**

Run:

```bash
cd android
ANDROID_SERIAL=emulator-5554 \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.feature.history.HistoryScreenInstrumentedTest
```

Expected: FAIL，失败原因是窄屏下筛选项不在可见区域，而不是设备离线、安装失败或测试进程崩溃。

### Task 4: 用换行布局修复外屏历史筛选

**Files:**
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenInstrumentedTest.kt`

**Interfaces:**
- Consumes: `HistoryStatusFilter.entries`、`DownloadPlatform.entries`、`HistoryPeriod.entries`。
- Produces: 不需要水平手势即可访问所有筛选项的 `HistoryFilters`。

- [ ] **Step 1: 删除水平滚动依赖并标记筛选容器**

删除 `horizontalScroll` 与 `rememberScrollState` import；在筛选根容器加入：

```kotlin
Modifier.testTag("history-filters")
```

测试文件同时加入：

```kotlin
import androidx.compose.ui.test.onNodeWithTag
```

- [ ] **Step 2: 用 FlowRow 呈现三个独立筛选组**

引入 `androidx.compose.foundation.layout.ExperimentalLayoutApi` 与 `FlowRow`，并在 `HistoryFilters` 上加入 `@OptIn(ExperimentalLayoutApi::class)`。将两个横向滚动 `Row` 改为三个 `FlowRow`：状态、平台、日期各自换行，统一使用：

```kotlin
horizontalArrangement = Arrangement.spacedBy(8.dp)
verticalArrangement = Arrangement.spacedBy(8.dp)
```

每组保留现有 `FilterChip` 的 selected/onClick 逻辑，不合并平台与日期状态，不引入第二份筛选真值。

- [ ] **Step 3: 运行定向仪器测试确认通过**

Run: Task 3 Step 2 的同一条定向命令。

Expected: `HistoryScreenInstrumentedTest` PASS；380dp 下四个断言均可见。

- [ ] **Step 4: 运行全量自动测试与 Debug 构建**

Run:

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`，Debug APK 生成；不把该结果写成 OPPO 真机通过。

- [ ] **Step 5: 提交外屏布局修复**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt \
  android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenInstrumentedTest.kt
git commit -m "fix(android): wrap history filters on outer screen"
```

### Task 5: 记录本阶段证据并进入下一阶段

**Files:**
- Create or Modify: `PROJECT_HANDOFF.md`

**Interfaces:**
- Consumes: JVM 测试、仪器测试、Debug 构建的真实命令和结果。
- Produces: 明确区分“已实现、自动测试、模拟器、真机、待验证风险”的动态交接。

- [ ] **Step 1: 更新交接证据**

记录以下固定字段：

```markdown
## 验证等级

- 已实现：Mac Chaquopy 路径可配置；历史筛选使用可换行布局。
- 定向契约通过：填写真实测试名称和结果。
- 构建通过：填写真实 Gradle 命令和 APK 路径。
- 模拟器验证：填写 AVD、Android 版本和定向测试结果。
- OPPO 真机验证：未在本阶段执行。
- 待验证风险：MP3 编码、三平台真实读取下载、断网恢复、通知、历史归档与 Find N5 内外屏切换。
```

- [ ] **Step 2: 提交交接更新**

```bash
git add PROJECT_HANDOFF.md docs/superpowers/plans/2026-07-16-mac-build-history-filter-continuation.md
git commit -m "docs(android): record Mac continuation evidence"
```

## 自检结果

- 本计划只覆盖 Mac 构建适配与外屏历史筛选，不宣称完成 MP3、三平台下载或 OPPO 真机验收。
- Windows 绝对路径、Cookie、账号数据与设备数据不会进入版本库。
- 每个生产代码改动前都有可观察的失败证据，每个完成声明都有对应命令与验证等级。
- 下一阶段必须单独设计并计划 MP3 原生编码链路；其后才进入真实平台与 OPPO 验收。
