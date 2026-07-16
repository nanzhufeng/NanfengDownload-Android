# Android 三页折叠屏视觉优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变下载与持久化语义的前提下，为首页、历史、设置落地薄荷绿工作台视觉系统，并按 OPPO Find N5 内外屏分别适配。

**Architecture:** `core/ui` 拥有颜色、字阶、卡片与选中态；三个页面只消费 Token 和共享组件。历史页的“仅完成”规则由 `filterCompletedHistory` 的唯一读取投影承担，HistoryScreen 不保留状态筛选状态；导航层继续是折叠形态与路由状态的唯一所有者。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Navigation Compose、JUnit 4、Compose UI instrumentation、Android Gradle。

## Global Constraints

- 全局主色固定为薄荷绿 `#F1F8F4` / 深林绿 `#1E6A45` / 选中绿 `#DCEFE3`；暖橙、紫、土色仅表达指定模块语义。
- 历史页只显示 `DownloadTaskStatus.COMPLETED`；状态筛选枚举、状态筛选控件和失败重试入口不再暴露。
- 历史打开、复制、分享、删除收纳进三点菜单；删除继续使用现有确认和 Repository 回调。
- 设置顺序固定为 YouTube、短视频平台（抖音、TikTok）；用 Material Icons Extended 的最近匹配图标，不手写或伪造品牌 SVG。
- 外屏维持单列 + BottomNavigation；内屏维持 NavigationRail + 双栏/双列，阈值仍为 `600.dp`。
- 不清数据、不卸载 OPPO 应用、不改 Room/Repository/下载或 MediaStore 业务语义。
- 任一新增行为必须先写失败测试、确认失败，再写最小实现。

---

## File Structure

- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/WorkbenchUi.kt` — 跨页面卡片、标题、筛选选中态和平台图标呈现。
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/NanzhufengTheme.kt` — 薄荷绿 ColorScheme、字阶、圆角及共享 Token。
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryFilter.kt` — 唯一的完成历史读取投影。
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt` — 时间线卡片、平台/时间筛选、更多菜单。
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt` — 首页层级、间距、Token 和非蓝色进度呈现。
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt` — 平台分组、图标、语义卡片和内屏网格。
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt` — 选中导航 Token、向设置传递内屏状态、删除历史重试回调。
- Modify: `android/app/src/test/java/com/nanzhufeng/videodownloader/feature/history/HistoryFilterTest.kt` — 仅完成投影的 JVM 合同。
- Modify: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenInstrumentedTest.kt` — 历史筛选和更多菜单的外屏 UI 合同。
- Modify: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation/NanzhufengAppInstrumentedTest.kt` — 内外屏导航与页面结构合同。
- Create: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreenInstrumentedTest.kt` — 账号排序、分组和内屏网格合同。
- Modify: `.gitignore` — 忽略本轮本地视觉审计证据目录 `.artifacts/`。
- Modify: `PROJECT_HANDOFF.md` — 实施后记录代码、构建、模拟器和 OPPO 双形态事实。

### Task 1: 将历史读取模型固定为“只完成”

**Files:**
- Modify: `android/app/src/test/java/com/nanzhufeng/videodownloader/feature/history/HistoryFilterTest.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryFilter.kt`

**Interfaces:**
- Consumes: `List<DownloadHistory>`、`DownloadPlatform?`、`HistoryPeriod`。
- Produces: `fun filterCompletedHistory(history: List<DownloadHistory>, query: String, platform: DownloadPlatform?, period: HistoryPeriod, now: Long = System.currentTimeMillis()): List<DownloadHistory>`。

- [ ] **Step 1: Write the failing test**

Replace the status-combination expectation with a test that explicitly passes a completed, failed, skipped and cancelled record:

```kotlin
@Test
fun completedProjection_excludesEveryNonCompletedStatusBeforeQueryAndPlatformFilters() {
    val result = filterCompletedHistory(
        history = listOf(
            history("done", "AI 进展", "作者甲", DownloadPlatform.YOUTUBE, DownloadTaskStatus.COMPLETED),
            history("failed", "AI 失败", "作者甲", DownloadPlatform.YOUTUBE, DownloadTaskStatus.FAILED),
            history("skipped", "AI 跳过", "作者乙", DownloadPlatform.DOUYIN, DownloadTaskStatus.SKIPPED),
            history("cancelled", "AI 取消", "作者乙", DownloadPlatform.TIKTOK, DownloadTaskStatus.CANCELLED),
        ),
        query = "AI",
        platform = null,
        period = HistoryPeriod.ALL,
        now = 2_000_000L,
    )

    assertEquals(listOf("done"), result.map(DownloadHistory::taskId))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest --tests com.nanzhufeng.videodownloader.feature.history.HistoryFilterTest`

Expected: compilation failure because `filterCompletedHistory` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add this public contract while retaining `HistoryStatusFilter` and `filterHistory` as a temporary source-compatible bridge for the unchanged `HistoryScreen` in Task 3. `filterHistory` must ignore its `status` argument and delegate to `filterCompletedHistory`, so the existing screen immediately consumes the completed-only projection. Task 3 removes both obsolete compatibility declarations together with the status UI. Retain `HistoryPeriod` and `DAY_MILLIS`:

```kotlin
fun filterCompletedHistory(
    history: List<DownloadHistory>,
    query: String,
    platform: DownloadPlatform?,
    period: HistoryPeriod,
    now: Long = System.currentTimeMillis(),
): List<DownloadHistory> {
    val normalizedQuery = query.trim()
    val cutoff = period.days?.let { now - it * DAY_MILLIS }
    return history.asSequence()
        .filter { it.finalStatus == DownloadTaskStatus.COMPLETED }
        .filter { normalizedQuery.isBlank() || it.title.contains(normalizedQuery, true) || it.creator.contains(normalizedQuery, true) || it.originalUrl.contains(normalizedQuery, true) }
        .filter { platform == null || it.platform == platform }
        .filter { cutoff == null || it.completedAt >= cutoff }
        .sortedByDescending(DownloadHistory::completedAt)
        .toList()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL` and both completed-projection and period tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryFilter.kt android/app/src/test/java/com/nanzhufeng/videodownloader/feature/history/HistoryFilterTest.kt
git commit -m "feat(android): show completed history by default"
```

### Task 2: 实现统一视觉 Token 与共享呈现组件

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/WorkbenchUi.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/NanzhufengTheme.kt`
- Modify: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation/NanzhufengAppInstrumentedTest.kt`

**Interfaces:**
- Produces: `AppCardTone`, `WorkbenchCard(tone, modifier, content)`, `SectionHeader(title, summary)`, `SelectedFilterChip(label, selected, onClick, modifier)` and `PlatformIcon(platform, contentDescription)`.
- Consumes: existing Material 3 colors and `DownloadPlatform`.

- [ ] **Step 1: Write the failing test**

Add this to `NanzhufengAppInstrumentedTest` after the compact navigation test:

```kotlin
@Test
fun compactNavigation_exposesSelectedDestinationState() {
    composeRule.setContent { app(expanded = false) }

    composeRule.onNodeWithTag("nav-home").assertIsDisplayed()
    composeRule.onNodeWithTag("nav-history").performClick()
    composeRule.onNodeWithTag("nav-history").assert(hasStateDescription("已选中"))
}
```

Add the local helper import `androidx.compose.ui.test.hasStateDescription`; extract the existing `NanzhufengApp(...)` setup into `private fun app(expanded: Boolean)` so the test compiles except for the missing selected state semantics.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.navigation.NanzhufengAppInstrumentedTest`

Expected: `compactNavigation_exposesSelectedDestinationState` fails because no navigation item exposes `stateDescription = 已选中`.

- [ ] **Step 3: Write minimal implementation**

Define the shared visual language in `NanzhufengTheme.kt`:

```kotlin
val MintWorkspace = Color(0xFFF1F8F4)
val ForestGreen = Color(0xFF1E6A45)
val SelectedSage = Color(0xFFDCEFE3)
val WarmOrange = Color(0xFFE86E2F)
val QualityPurple = Color(0xFF7250B5)
val StorageOchre = Color(0xFFB36A16)
```

Make these the Material primary/background/surfaceVariant values, add explicit `Typography` text styles for `headlineSmall`, `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium` and `labelMedium`, and increase `medium`/`large` shapes to `16.dp`/`20.dp`.

Create `WorkbenchUi.kt` with a `WorkbenchCard` whose tone maps to a low-saturation container color, a `SelectedFilterChip` using `FilterChipDefaults.filterChipColors(selectedContainerColor = SelectedSage)`, and `PlatformIcon` using Material Icons Extended (`PlayCircle` for YouTube and `VideoLibrary` / `SmartDisplay` for the two short-video platforms). Every icon receives the caller-provided accessible description.

In both navigation item lambdas, attach:

```kotlin
Modifier.testTag(destination.testTag).semantics {
    stateDescription = if (currentRoute == destination.route) "已选中" else "未选中"
}
```

and replace the hard-coded blue indicator with `SelectedSage`.

- [ ] **Step 4: Run test to verify it passes**

Run the command from Step 2. Expected: all navigation instrumentation tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/NanzhufengTheme.kt android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/WorkbenchUi.kt android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation/NanzhufengAppInstrumentedTest.kt
git commit -m "feat(android): add mint workbench design system"
```

### Task 3: 重构历史页为完成时间线与三点菜单

**Files:**
- Modify: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenInstrumentedTest.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryFilter.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt`

**Interfaces:**
- Consumes: `filterCompletedHistory`, existing delete callback and Android Intent/clipboard actions.
- Produces: test tags `history-platform-time-filters`, `history-overflow-<taskId>` and `history-card-<taskId>`; HistoryScreen no longer accepts `onRetry`.

- [ ] **Step 1: Write the failing test**

Replace the old status-filter layout assertion with:

```kotlin
@Test
fun outerScreen_showsPlatformAndTimeFiltersWithoutStatusFilters() {
    composeRule.setContent { HistoryScreen(history = emptyList(), onDeleteRecord = {}) }

    composeRule.onNodeWithTag("history-platform-time-filters").assertIsDisplayed()
    composeRule.onNodeWithText("全部平台").assertIsDisplayed()
    composeRule.onNodeWithText("近 30 天").assertIsDisplayed()
    composeRule.onNodeWithText("已跳过").assertDoesNotExist()
    composeRule.onNodeWithText("已取消").assertDoesNotExist()
}
```

Add a second test that supplies one completed record, clicks `history-overflow-completed`, and asserts `复制原链接`, `分享原链接` and `删除历史记录` are displayed only after the menu opens.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.feature.history.HistoryScreenInstrumentedTest`

Expected: tests fail because status chips remain and no overflow test tag/menu exists.

- [ ] **Step 3: Write minimal implementation**

Remove `status` state, `HistoryStatusFilter`, `onStatusChange`, `StatusChip`, retry UI, error-summary UI and `onRetry` from HistoryScreen. Call:

```kotlin
val filtered = filterCompletedHistory(history, query, platform, period)
```

Keep only platform/time FlowRows under `Modifier.testTag("history-platform-time-filters")`. Render each completed record with a pale-green `WorkbenchCard`, a leading date/time column, a 10dp green marker/vertical rail, title, platform/specification metadata, and a three-dot `IconButton` tagged `history-overflow-${item.taskId}`. Its `DropdownMenu` contains existing open-file (only when available), copy, share and delete actions; selecting delete sets `pendingDeleteId` exactly as today.

Remove the obsolete `onRetryHistory` parameter and lambda from `AppNavHost` and both NanzhufengApp calls. The Repository `retryHistory` method remains untouched for unrelated callers.

- [ ] **Step 4: Run test to verify it passes**

Run the command from Step 2. Expected: both history screen tests pass and no status-filter text is found.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenInstrumentedTest.kt
git commit -m "feat(android): refine completed history timeline"
```

### Task 4: 调整首页层级、色彩与内外屏工作区

**Files:**
- Modify: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation/NanzhufengAppInstrumentedTest.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt`

**Interfaces:**
- Consumes: existing `HomeScreen` callbacks and `expanded` boolean.
- Produces: honest compact empty activity state, shared CardTone usage and retained test tags `formal-expanded-workbench`, `formal-expanded-queue`, `formal-total-progress`, `formal-read-entry`.

- [ ] **Step 1: Write the failing test**

Add this test:

```kotlin
@Test
fun compactHome_showsHonestActiveEmptyStateAndReadEntry() {
    composeRule.setContent { app(expanded = false) }

    composeRule.onNodeWithText("当前没有下载任务").assertIsDisplayed()
    composeRule.onNodeWithTag("formal-queue-tabs").assertIsDisplayed()
    composeRule.onNodeWithTag("formal-read-entry").assertIsDisplayed()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run the navigation instrumentation command from Task 2. Expected: this test fails because compact Home only creates an active card when a task is downloading.

- [ ] **Step 3: Write minimal implementation**

In `CompactHome`, always render either `ActiveDownloadCard` or `EmptyActiveCard` directly after `HomeHeader`. Replace `PrussianBlue`, hard-coded blue progress colors and old white cards with `WorkbenchCard` tones and `MaterialTheme.colorScheme.primary`/`WarmOrange`/semantic status tokens. Keep the existing content order: header, current download/empty state, queue, total progress, input/read action.

For `ExpandedHome`, retain the existing two-column topology but make the left primary column a 1.0 weight and the queue 1.12 weight; remove the artificial spacer that pushes input against the bottom, use `Arrangement.spacedBy(16.dp)`, and give each column independently bounded scrollable content where necessary. Keep `formal-expanded-workbench` and `formal-expanded-queue` unchanged for the existing width-contract tests.

- [ ] **Step 4: Run test to verify it passes**

Run the navigation instrumentation command from Task 2. Expected: the new compact empty-state test and existing compact/expanded structural tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation/NanzhufengAppInstrumentedTest.kt
git commit -m "feat(android): refine responsive home workbench"
```

### Task 5: 重构设置卡片、平台分组与内屏网格

**Files:**
- Create: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreenInstrumentedTest.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt`

**Interfaces:**
- Changes `SettingsScreen` to accept `expanded: Boolean`.
- Produces tags `settings-youtube`, `settings-short-video-platforms`, `settings-douyin`, `settings-tiktok`, `settings-quality-card`, `settings-download-card`, `settings-storage-card` and `settings-expanded-grid`.

- [ ] **Step 1: Write the failing test**

Create the test class with these two cases:

```kotlin
@Test
fun accounts_putYoutubeBeforeGroupedShortVideoPlatforms() {
    composeRule.setContent { SettingsScreen(settings(), sessions(), {}, {}, {}, {}, {}, expanded = false) }

    composeRule.onNodeWithTag("settings-youtube").assertIsDisplayed()
    composeRule.onNodeWithTag("settings-short-video-platforms").assertIsDisplayed()
    composeRule.onNodeWithTag("settings-douyin").assertIsDisplayed()
    composeRule.onNodeWithTag("settings-tiktok").assertIsDisplayed()
}

@Test
fun expandedSettings_usesDedicatedTwoColumnGrid() {
    composeRule.setContent { SettingsScreen(settings(), sessions(), {}, {}, {}, {}, {}, expanded = true) }

    composeRule.onNodeWithTag("settings-expanded-grid").assertIsDisplayed()
    composeRule.onNodeWithTag("settings-quality-card").assertIsDisplayed()
    composeRule.onNodeWithTag("settings-storage-card").performScrollTo().assertIsDisplayed()
}
```

Use factory helpers returning `AppSettings()` and three `SiteSessionState` values so the test exercises real composables without repository mocks.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.feature.settings.SettingsScreenInstrumentedTest`

Expected: compilation failure because `expanded` and the required semantic tags do not exist.

- [ ] **Step 3: Write minimal implementation**

Add `expanded: Boolean` to `SettingsScreen`, and pass the current AppNavHost `expanded` argument through its call. Build a `SettingsContent` list in fixed order: account card, quality card, download behavior card, storage card, content boundary card. In compact mode show that list in the current `LazyColumn`; in expanded mode use `LazyVerticalGrid(columns = GridCells.Fixed(2))` tagged `settings-expanded-grid`, with the account card spanning both columns and the remaining semantic cards occupying grid cells.

Replace `SessionSite.entries` iteration with explicit presentation order:

```kotlin
val youtube = stateFor(SessionSite.YOUTUBE)
val douyin = stateFor(SessionSite.DOUYIN)
val tiktok = stateFor(SessionSite.TIKTOK)
```

Render YouTube as the first `SessionRow` tagged `settings-youtube`. Render the short-video heading and the two adjacent `SessionRow`s inside `settings-short-video-platforms`, tagged `settings-douyin` and `settings-tiktok`; every row starts with `PlatformIcon`. Use `WorkbenchCard` tones: purple for quality, green for download behavior, ochre for storage, orange for content/reading context. Preserve every current button, launcher and callback.

- [ ] **Step 4: Run test to verify it passes**

Run the command from Step 2. Expected: both SettingsScreen instrumentation tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreenInstrumentedTest.kt
git commit -m "feat(android): refine adaptive settings cards"
```

### Task 6: 全量回归、真机双形态检查与交接

**Files:**
- Modify: `.gitignore`
- Modify: `PROJECT_HANDOFF.md`

**Interfaces:**
- Consumes: existing installed debug package, OPPO device `3B157F009E800000`, `cmd device_state state 3/reset` fold control.
- Produces: independent external/inner screenshots under `.artifacts/ui-audit-2026-07-16/`; this directory stays untracked.

- [ ] **Step 1: Write the failing test/check**

Before code finalization, run the complete static gate:

```bash
cd android && JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' NANZHUFENG_BUILD_PYTHON=/opt/homebrew/bin/python3.13 ./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected before final fixes: either failure identifies a missing integration import/tag, or all automated regression checks are green and the task proceeds to device verification.

- [ ] **Step 2: Apply non-production housekeeping**

Add exactly this ignore entry under the local preview section:

```gitignore
.artifacts/
```

Do not remove the existing local screenshots; only prevent them from being staged.

- [ ] **Step 3: Verify build and install without data loss**

Run the full command from Step 1 until it reports `BUILD SUCCESSFUL`. Then use `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` against `3B157F009E800000`; do not use uninstall, `pm clear`, or a downgrade flag.

- [ ] **Step 4: Perform independent visual and continuity checks**

Capture each route independently on the 1140 × 2616 external screen: Home, History, Settings. Expand with `adb shell cmd device_state state 3`, capture the same three routes at 2248 × 2480, then reset with `adb shell cmd device_state state reset`. Start each route before its capture; do not compose a multi-route image. Confirm the selected navigation item, history completed-only card/menu, settings order and absence of white icon backing. Fold once while History has a platform/time condition and Home has input text, then confirm route/input/filter/task state remains.

- [ ] **Step 5: Record actual evidence and commit**

Update `PROJECT_HANDOFF.md` with separate facts for implementation, automated checks, OPPO external screen, OPPO inner screen, fold continuity, and any residual risk. Commit only source, tests, `.gitignore`, the updated handoff, and this plan:

```bash
git add .gitignore PROJECT_HANDOFF.md docs/superpowers/plans/2026-07-16-android-three-page-foldable-visual-refinement-plan.md
git commit -m "docs(android): record three-page foldable visual verification"
```

## Plan Self-Review

- Spec coverage: Task 1 covers the only-completed data contract; Tasks 2-5 cover shared tokens, navigation selection, home, history, settings, platform order/icons and adaptive layouts; Task 6 covers artifact hygiene, build, OPPO dimensions, fold continuity and handoff.
- Placeholder scan: no TBD/TODO/deferred implementation text; every task has concrete paths, test command, expected result, minimal implementation and commit.
- Type consistency: `filterCompletedHistory` is the completed-only owner; Task 1 keeps a temporary source-compatible `filterHistory` bridge until Task 3 removes its status UI and obsolete declarations. SettingsScreen gains `expanded` only at the existing AppNavHost caller; history retry is removed only from the page/navigation callback path, not from Repository.
