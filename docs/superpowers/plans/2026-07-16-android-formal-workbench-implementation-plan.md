# Android 正式下载工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 Android 技术验证壳替换为 OPPO Find N5 外屏和内屏可用的正式下载工作台，实现“智能读取 -> 选择 -> 队列 -> 下载 -> 历史”的真实链路。

**Architecture:** 正式功能由 `SourceDiscoveryEngine`、`DownloadRepository`、`DownloadEngine`、`SessionProvider` 四个所有者承担。现有 `probe` 包中的 yt-dlp、WebView 嗅探、HTTP 下载和媒体校验只能作为内部适配器，`ProbeScreen` 不得再被任何用户路由、首页按钮或设置入口访问。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room、DataStore、Chaquopy + yt-dlp、Android WebView、MediaStore、Media3、协程、WorkManager 前台执行。

## Global Constraints

- 仅支持公开内容和当前登录账号有权限访问的内容；不实现会员、付费、DRM、私密内容绕过。
- 单视频链接只读取一个作品；作者、频道、作品页或播放列表才读取多个作品。
- 抖音、TikTok 作者列表必须按作者标识过滤，禁止混入推荐或其他作者作品；不得把 500 作为固定上限，使用“加载更多”。
- 取消勾选的作品不得进入调度；每个作品可以独立修改分辨率。
- 默认输出目录为 `Movies/南烛枫视频下载器/<平台>/<博主>/`；文件名为 `YYYY-MM-DD 标题.ext`；已存在且校验通过的同作品快速跳过。
- 首页、历史、设置中不得展示 `ProbeScreen`、Python/yt-dlp 检查、手动嗅探、写入 Movies 等技术验证按钮。
- UI 以 Find N5 外屏 `1140 x 2616` 和内屏 `2248 x 2480` 为基准；`600.dp` 及以上使用左侧窄导航栏 + 左右工作区，以下使用底部导航。
- 状态必须可区分：下载中蓝色、等待金色、等待网络橙色、已跳过紫灰色、完成绿色、失败红色、暂停灰色。
- 真实设备验证采用覆盖安装；不得卸载应用、清空应用数据或重置用户登录信息。

---

## 文件边界

| 路径 | 责任 |
| --- | --- |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery/` | 链接分类、单视频读取、作者/频道分页读取、平台与作者过滤。 |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/` | 真正的下载调度、暂停/取消、网络恢复、文件去重、校验与历史归档。 |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/` | Room 队列、传输进度、分辨率、批量操作和历史的唯一数据入口。 |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/` | 正式首页、下载中卡片、队列列表、总进度、两到三行输入框和智能读取。 |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/` | 可检索、可筛选、可重试、可打开所在位置的下载历史。 |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/` | 存储位置、默认分辨率、登录会话、网络恢复、并发和命名设置。 |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt` | 仅保留首页、历史、设置三条公开路由。 |
| `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/` | 内部适配器；不得成为公开 Compose 页面或导航目标。 |

## Task 1: 固化正式读取契约并阻断技术探针路由

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery/DiscoveryModels.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery/SourceDiscoveryEngine.kt`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/domain/discovery/SourceDiscoveryEngineTest.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation/NanzhufengAppInstrumentedTest.kt`

**Interfaces:**
- Consumes: `probe.UrlClassifier`, `probe.YtDlpProbe`, `probe.DouyinCaptureStore`。
- Produces: `SourceDiscoveryEngine.read(input: String, page: Int = 1): DiscoveryResult`，供首页 ViewModel 调用。

- [ ] **Step 1: 写入失败的路由测试**

```kotlin
@Test
fun smartRead_staysOnHome_andNeverShowsProbeControls() {
    composeRule.setContent { NanzhufengApp(fakeDownloads, fakeSettings, onOpenDouyin = {}) }
    composeRule.onNodeWithText("智能读取").performClick()
    composeRule.onNodeWithText("检查 Python/yt-dlp").assertDoesNotExist()
    composeRule.onNodeWithTag("home-screen").assertExists()
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.navigation.NanzhufengAppInstrumentedTest`

Expected: 当前实现失败，因为 `onSmartRead` 导航至 `PROBE_ROUTE`。

- [ ] **Step 3: 建立正式读取模型和接口**

```kotlin
sealed interface DiscoveryResult {
    data class Single(val item: DiscoveredMedia) : DiscoveryResult
    data class Collection(val owner: CreatorIdentity, val items: List<DiscoveredMedia>, val hasMore: Boolean) : DiscoveryResult
    data class Failure(val message: String) : DiscoveryResult
}

interface SourceDiscoveryEngine {
    suspend fun read(input: String, page: Int = 1): DiscoveryResult
}
```

`DiscoveredMedia` 必须包含 `sourceUrl`、`platform`、`mediaId`、`title`、`creator`、`creatorId`、`publishedAt`、`thumbnailUrl` 和默认 `ResolutionPreset`。`NanzhufengApp` 删除 `PROBE_ROUTE`、`probeInput`、`onOpenProbe` 和 `ProbeScreen` 引用；设置页删除探针入口。

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.navigation.NanzhufengAppInstrumentedTest`

Expected: PASS，首页无技术验证按钮，三页导航仍可用。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery \
        android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt \
        android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings \
        android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation
git commit -m "feat(android): 移除公开技术探针入口"
```

### Task 2: 实现严格的平台读取与分页过滤

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery/PlatformSourceDiscoveryEngine.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery/ProbeDiscoveryGateway.kt`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/domain/discovery/PlatformSourceDiscoveryEngineTest.kt`
- Modify: `android/app/src/main/python/nanzhufeng_probe/youtube_probe.py`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/YtDlpProbe.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/DouyinCaptureStore.kt`

**Interfaces:**
- Consumes: `SourceDiscoveryEngine`、yt-dlp 的 `resolve_source`、`extract_single`、`extract_creator`。
- Produces: `read(input, page)` 对单视频只返回 `DiscoveryResult.Single`；对集合只返回经过作者 ID 过滤和去重的 `DiscoveryResult.Collection`。

- [ ] **Step 1: 写入失败的纯 Kotlin 单元测试**

```kotlin
@Test
fun youtubeWatchUrl_returnsOnlyOneItem() = runTest {
    val result = engine.read("https://www.youtube.com/watch?v=abc")
    assertThat(result).isInstanceOf(DiscoveryResult.Single::class.java)
}

@Test
fun creatorCatalog_dropsForeignCreators_andUsesLoadMore() = runTest {
    val result = engine.read("https://www.tiktok.com/@creator", page = 2) as DiscoveryResult.Collection
    assertThat(result.items.map { it.creatorId }).containsOnly("creator-id")
    assertThat(result.hasMore).isTrue()
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests "*PlatformSourceDiscoveryEngineTest"`

Expected: FAIL，因为正式引擎不存在。

- [ ] **Step 3: 以网关适配现有解析能力**

```kotlin
class PlatformSourceDiscoveryEngine(
    private val gateway: ProbeDiscoveryGateway,
) : SourceDiscoveryEngine {
    override suspend fun read(input: String, page: Int): DiscoveryResult =
        when (gateway.resolveKind(input)) {
            SourceKind.SINGLE -> DiscoveryResult.Single(gateway.readSingle(input))
            SourceKind.COLLECTION -> gateway.readCollection(input, page)
        }
}
```

Python 侧将 `extract_creator` 改为支持 YouTube、TikTok 和 yt-dlp 能识别的抖音公开作者/作品集合；集合结果必须输出 `owner_creator_id`，对每个条目执行 `creator_id == owner_creator_id` 的强过滤。短链先解析为最终链接再判断单视频或集合。抖音 WebView 嗅探仅用于单作品实际媒体流获取，且必须校验 `workId` 与输入目标一致。

- [ ] **Step 4: 运行读取测试**

Run: `./gradlew testDebugUnitTest --tests "*PlatformSourceDiscoveryEngineTest"`

Expected: PASS，单链接为一项；作者列表无外部作者；第二页通过 `hasMore` 表示可继续加载。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery \
        android/app/src/main/java/com/nanzhufeng/videodownloader/probe \
        android/app/src/main/python/nanzhufeng_probe/youtube_probe.py \
        android/app/src/test/java/com/nanzhufeng/videodownloader/domain/discovery
git commit -m "feat(android): 添加严格平台作品读取"
```

### Task 3: 扩展 Room 队列为正式下载状态机

**Files:**
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/AppDatabase.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/dao/DownloadDaos.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/DownloadRepository.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepository.kt`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepositoryTest.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepositoryInstrumentedTest.kt`

**Interfaces:**
- Consumes: `DiscoveredMedia`。
- Produces: `enqueueAll(items: List<DiscoveredMedia>)`、`updateTransfer(taskId, downloadedBytes, totalBytes, speedBytesPerSecond, remainingSeconds)`、`setResolution(taskId, preset)`、`setSelected(taskId, selected)`、`bulkSelect(ids, selected)`、`transition(taskId, status)`。

- [ ] **Step 1: 写入失败的仓库测试**

```kotlin
@Test
fun deselectedTask_isNeverReturnedAsNextDownload() = runTest {
    repository.enqueueAll(listOf(first, second))
    repository.setSelected(second.id, false)
    assertThat(repository.nextSelectedTask()).isEqualTo(first.id)
}

@Test
fun updateTransfer_persistsProgressAndRemainingTime() = runTest {
    repository.updateTransfer("task-1", 50, 100, 10, 5)
    assertThat(repository.activeTasks.first().single().downloadedBytes).isEqualTo(50)
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests "*RoomDownloadRepositoryTest"`

Expected: FAIL，因为批量操作、传输进度和下一个已选任务入口未定义。

- [ ] **Step 3: 添加 DAO 和仓库唯一写入口**

```kotlin
interface DownloadRepository {
    suspend fun enqueueAll(items: List<DiscoveredMedia>)
    suspend fun nextSelectedTask(): QueuedDownload?
    suspend fun updateTransfer(taskId: String, downloaded: Long, total: Long, speed: Long, remaining: Long?)
    suspend fun setResolution(taskId: String, preset: ResolutionPreset)
    suspend fun bulkSelect(taskIds: Set<String>, selected: Boolean)
}
```

Room 迁移不得破坏已有 `media_items`、`download_tasks`、`download_history` 数据；所有状态变化只能通过 `RoomDownloadRepository`，禁止 UI 直接写 DAO。

- [ ] **Step 4: 运行仓库测试**

Run: `./gradlew testDebugUnitTest --tests "*RoomDownloadRepositoryTest" && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.data.repository.RoomDownloadRepositoryInstrumentedTest`

Expected: PASS，未勾选任务不会被返回，进度、分辨率、状态均持久化。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/data \
        android/app/src/test/java/com/nanzhufeng/videodownloader/data \
        android/app/src/androidTest/java/com/nanzhufeng/videodownloader/data
git commit -m "feat(android): 完善正式下载队列状态机"
```

### Task 4: 实现可恢复的真实下载与文件跳过

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DownloadEngine.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/ForegroundDownloadWorker.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/OutputFilePolicy.kt`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/domain/download/OutputFilePolicyTest.kt`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/HttpFileDownloader.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/MediaStoreProbe.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `DownloadRepository.nextSelectedTask()`、`SourceDiscoveryEngine.read()`、`HttpFileDownloader`、`MediaFileValidator`。
- Produces: `DownloadEngine.start()`、`pauseAll()`、`stop(taskId)`、`resumeWhenNetworkAvailable()`，并将每个任务归档至历史。

- [ ] **Step 1: 写入失败的文件策略测试**

```kotlin
@Test
fun outputFile_usesPlatformCreatorAndPublishedDate() {
    val output = policy.relativePath(media("抖音", "博主A", "20260716", "标题"))
    assertThat(output).isEqualTo("Movies/南烛枫视频下载器/抖音/博主A/2026-07-16 标题.mp4")
}

@Test
fun verifiedExistingMedia_isMarkedSkippedWithoutNetworkCall() = runTest {
    engine.start()
    assertThat(repository.activeTasks.first().single().status).isEqualTo(DownloadTaskStatus.SKIPPED)
    assertThat(gateway.singleReads).isEqualTo(0)
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests "*OutputFilePolicyTest"`

Expected: FAIL，因为正式输出策略和下载引擎不存在。

- [ ] **Step 3: 实现前台调度和断网恢复**

```kotlin
interface DownloadEngine {
    suspend fun start()
    suspend fun pauseAll()
    suspend fun stop(taskId: String)
    suspend fun resumeWhenNetworkAvailable()
}
```

每次开始任务先以媒体 ID、目标文件名和媒体校验器检查已有文件；命中后直接写入 `SKIPPED`。未命中时重新解析单项的短时效流，再进行断点下载、必要的音视频合成、媒体校验和 MediaStore 写入。网络异常写入 `WAITING_NETWORK`，由带网络约束的前台 Worker 在恢复网络后自动重试；用户暂停和停止不得自动重试。

- [ ] **Step 4: 运行本地传输与设备测试**

Run: `./gradlew testDebugUnitTest --tests "*OutputFilePolicyTest" && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.probe.MediaStoreProbeInstrumentedTest`

Expected: PASS，本地媒体可写入 Movies，已存在文件被快速跳过，网络恢复后的任务回到队列。

- [ ] **Step 5: 提交**

```bash
git add android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml \
        android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download \
        android/app/src/main/java/com/nanzhufeng/videodownloader/probe \
        android/app/src/test/java/com/nanzhufeng/videodownloader/domain/download
git commit -m "feat(android): 添加可恢复正式下载引擎"
```

### Task 5: 按 Find N5 方案实现正式首页与队列管理

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/HomeViewModel.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/HomeUiState.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/HomeScreen.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/QueueList.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/ActiveDownloadCard.kt`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/feature/home/HomeViewModelTest.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/home/HomeScreenInstrumentedTest.kt`

**Interfaces:**
- Consumes: `SourceDiscoveryEngine`、`DownloadRepository`、`DownloadEngine`、`SettingsRepository`。
- Produces: `HomeUiState`，其中包含输入文本、当前下载、队列分类、总进度、选择集合和 `loadMore()`。

- [ ] **Step 1: 写入失败的首页状态测试**

```kotlin
@Test
fun smartRead_singleLink_replacesQueueWithOneSelectedItem() = runTest {
    viewModel.onInputChanged("https://www.youtube.com/watch?v=one")
    viewModel.smartRead()
    assertThat(viewModel.uiState.value.queueItems).hasSize(1)
    assertThat(viewModel.uiState.value.queueItems.single().selected).isTrue()
}

@Test
fun loadMore_isVisibleOnlyForCollectionWithMorePages() = runTest {
    viewModel.smartRead()
    assertThat(viewModel.uiState.value.canLoadMore).isTrue()
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests "*HomeViewModelTest"`

Expected: FAIL，因为首页没有 ViewModel、没有正式读取状态。

- [ ] **Step 3: 实现外屏和内屏工作台**

外屏顺序固定为：应用头部与网络状态、当前下载卡、队列三标签、总进度、两到三行链接输入框、单个橙色“智能读取”按钮、底部“首页/历史/设置”。内屏固定为：左侧窄导航栏；左工作区为当前下载、总进度和输入读取；右工作区为队列三标签和批量管理。队列行提供拖动排序、序号、封面、标题、博主、独立分辨率、状态、大小和更多菜单；不使用假视频或假下载进度填充空状态。

```kotlin
data class HomeUiState(
    val input: String = "",
    val active: QueuedDownload? = null,
    val queueItems: List<QueuedDownload> = emptyList(),
    val selectedTab: QueueTab = QueueTab.QUEUE,
    val canLoadMore: Boolean = false,
    val totalProgress: TotalProgress = TotalProgress.Empty,
)
```

- [ ] **Step 4: 运行 Compose 测试**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.feature.home.HomeScreenInstrumentedTest`

Expected: PASS，外屏显示输入框在总进度下方，内屏显示左右工作区，技术探针控件不存在。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home \
        android/app/src/test/java/com/nanzhufeng/videodownloader/feature/home \
        android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/home
git commit -m "feat(android): 实现 Find N5 正式下载工作台"
```

### Task 6: 完成正式历史、设置和登录会话入口

**Files:**
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/session/SessionProvider.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/session/WebViewSessionProvider.kt`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreenTest.kt`
- Create: `android/app/src/test/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `DownloadRepository.history`、`SettingsRepository`、`DownloadEngine`。
- Produces: 历史筛选/搜索/重试/打开位置和设置项（存储位置、默认分辨率、并发、网络恢复、命名规则、登录状态）。

- [ ] **Step 1: 写入失败的设置和历史测试**

```kotlin
@Test
fun history_retry_requeuesFailedItemOnly() = runTest {
    viewModel.retry("failed-task")
    assertThat(repository.requeuedIds).containsExactly("failed-task")
}

@Test
fun settings_hasNoTechnicalProbeEntry() {
    composeRule.setContent { SettingsScreen(settings, actions) }
    composeRule.onNodeWithText("检查 Python/yt-dlp").assertDoesNotExist()
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew testDebugUnitTest --tests "*HistoryScreenTest" --tests "*SettingsScreenTest"`

Expected: FAIL，因为历史重试和正式设置动作尚未定义。

- [ ] **Step 3: 实现可操作的历史与设置**

登录仅通过站点专用 WebView 会话入口保存 Cookie；会话数据保留在应用私有目录，应用重启后可复用。设置显示账号状态和“重新登录”，不暴露浏览器探针或网络流嗅探。历史提供平台、时间、状态筛选、关键词搜索、打开所在位置、删除记录和重试失败/等待网络项。

- [ ] **Step 4: 运行测试**

Run: `./gradlew testDebugUnitTest --tests "*HistoryScreenTest" --tests "*SettingsScreenTest"`

Expected: PASS，技术验证入口不再出现，历史与设置动作通过仓库调用。

- [ ] **Step 5: 提交**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history \
        android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings \
        android/app/src/main/java/com/nanzhufeng/videodownloader/domain/session \
        android/app/src/test/java/com/nanzhufeng/videodownloader/feature
git commit -m "feat(android): 完成历史设置与登录会话"
```

### Task 7: 端到端验收与交接

**Files:**
- Modify: `docs/nanzhufeng-video-downloader-development-context-for-chatgpt.md`
- Create: `docs/android-formal-workbench-verification.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: Tasks 1 至 6 的正式功能。
- Produces: 可复现的真实设备验证记录、已验证与未验证边界、下一次继续开发提示词。

- [ ] **Step 1: 写入端到端验收清单**

```markdown
- [ ] YouTube 单视频智能读取后仅出现 1 项。
- [ ] TikTok 单视频智能读取后仅出现 1 项。
- [ ] 抖音单视频智能读取后仅出现 1 项。
- [ ] 作者/频道列表仅包含目标作者，点击加载更多后继续追加且无重复。
- [ ] 取消勾选的项目不下载。
- [ ] 断网任务标记等待网络，恢复网络后继续；用户暂停的任务不自动继续。
- [ ] 已存在且通过校验的媒体快速跳过。
- [ ] 全部完成后出现完成通知；历史可看到结果。
- [ ] 外屏与内屏均没有 ProbeScreen 或技术验证按钮。
```

- [ ] **Step 2: 编译与单元测试**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 覆盖安装并执行真实设备验收**

Run: `adb -s 3B157F009E800000 install -r android/app/build/outputs/apk/debug/app-debug.apk`

Expected: 覆盖安装成功；不卸载、不清空数据。逐项执行验收清单，并将实际链接、结果、失败原因写入验证文档。

- [ ] **Step 4: 更新项目上下文和交接内容**

开发上下文必须将“正式工作台”和“已验证的下载能力”分开记录；未完成的平台能力、第三方站点限制、真实设备未通过项必须列为待验证风险。

- [ ] **Step 5: 提交**

```bash
git add README.md docs/android-formal-workbench-verification.md docs/nanzhufeng-video-downloader-development-context-for-chatgpt.md
git commit -m "docs(android): 记录正式工作台验收结果"
```

## 自检结果

- 方案覆盖：公开三页、Find N5 两种布局、单视频与集合分流、三平台作者过滤、选择/分辨率、真实下载、网络恢复、文件跳过、历史、设置、会话、验收均对应任务 1 至 7。
- 约束覆盖：访问权限、禁止绕过、无固定 500 上限、取消勾选不下载、无公开技术探针、保留用户数据均列入全局约束。
- 风险公开：现有抖音 WebView 嗅探为单作品媒体流能力；Task 2 必须用真实抖音作者页验证 yt-dlp 的集合读取与作者过滤，未通过前不得宣称抖音作者批量下载完成。
- 占位检查：本计划不包含 TBD、TODO 或“稍后实现”等占位描述。
