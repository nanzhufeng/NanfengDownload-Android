# Android Room 状态模型与三页壳层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已通过 Find N5 可行性关卡的 Android 工程中建立 Room 任务/历史单一数据源，并实现首页、历史、设置三个可响应 OPPO Find N5 外屏和内屏的应用壳层。

**Architecture:** 保持单一 `app` 模块。Room 是媒体、队列和历史的唯一持久化所有者，DataStore 是用户设置的唯一持久化所有者；Compose 页面只订阅 repository 的 `Flow`，不在 Activity 或页面内保存业务真相。现有 `probe` 下载链路继续保留，通过设置页“技术验证”入口访问，本阶段不把探针状态伪装成正式队列。

**Tech Stack:** Kotlin 2.0.20、Jetpack Compose Material 3、Room 2.6.1、KSP 2.0.20-1.0.25、Navigation Compose 2.8.0、Preferences DataStore 1.1.1、Chaquopy Python 3.13、JUnit 4、AndroidX Compose UI Test。

## Global Constraints

- 工作目录固定为 `D:\CodexProjects\CleanVideoDownloader-AndroidProbe`，分支固定为 `feature/android-room-shell`。
- 只修改 `android/` 与本阶段对应文档，不修改 Windows 桌面版行为和打包链路。
- Room 中的任务和历史是唯一业务真相；Compose 页面不得维护第二套队列或终态判断。
- DataStore 只保存默认分辨率、保存位置类型和输入草稿等设置，不保存账号密码或 Cookie 明文。
- 本阶段不实现 WorkManager/前台服务、完整智能读取、批量下载、登录、自定义目录和文件删除。
- 现有 `ProbeScreen`、YouTube/抖音/TikTok 真机探针和下载实现必须保留，设置页提供“技术验证”入口。
- 外屏使用底部导航；宽度达到 `840.dp` 时使用左侧 NavigationRail。关键操作不得放在折痕区域。
- 首页链接输入框显示 3 行，只保留单一“智能读取”按钮；本阶段该按钮进入现有探针过渡入口，不新增第二套解析逻辑。
- 视觉采用普鲁士蓝 `#0D3A69` 为主色、爱马仕橙 `#EB5C20` 为强调色、马尔斯绿 `#018B8D` 为选择/成功辅助色；同一页面只让主色和强调色承担操作焦点。
- 状态同时使用文字和颜色：等待黄、下载中蓝、完成绿、失败红、等待网络橙、跳过灰；不能只用颜色表达。
- 所有 Gradle 测试从 ASCII 工作树执行；不得把 `build/`、APK、数据库、Cookie 或下载媒体提交到 Git。
- 自动测试通过不等于 Find N5 真实验证；本阶段至少验证数据库跨 Activity 重建、外/内屏布局和三页导航。

---

## File Structure

- `android/app/src/main/java/com/nanzhufeng/videodownloader/core/model/DownloadModels.kt`：平台无关的媒体、任务、历史、分辨率和状态模型。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/core/model/TaskTransitionPolicy.kt`：任务状态转换的唯一判定入口。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/entity/Entities.kt`：Room 实体和索引。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/dao/DownloadDaos.kt`：媒体、任务和历史 DAO。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/NanzhufengDatabase.kt`：Room database version 1。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/DownloadRepository.kt`：队列与历史公开接口。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepository.kt`：事务写入、状态更新和历史归档。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/data/settings/SettingsRepository.kt`：默认分辨率和输入草稿 DataStore。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/AppContainer.kt`：数据库和 repository 的组合根。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/NanzhufengApplication.kt`：继承 Chaquopy `PyApplication` 并持有 `AppContainer`。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/NanzhufengTheme.kt`：颜色、形状和 Material 主题。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt`：三页导航、外/内屏切换和探针过渡路由。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/HomeScreen.kt`：首页壳层。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt`：历史列表壳层。
- `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt`：设置壳层和技术验证入口。

---

### Task 1: 依赖、领域模型与任务状态机

**Files:**
- Modify: `android/build.gradle.kts`
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/core/model/DownloadModels.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/core/model/TaskTransitionPolicy.kt`
- Test: `android/app/src/test/java/com/nanzhufeng/videodownloader/core/model/TaskTransitionPolicyTest.kt`

**Interfaces:**
- Consumes: 既有 `probe.Platform` 和 `probe.SourceKind` 的业务含义，不直接依赖探针 UI。
- Produces: `DownloadPlatform`、`DownloadSourceKind`、`ResolutionPreset`、`DownloadTaskStatus`、`MediaItem`、`DownloadTask`、`DownloadHistory`、`TaskTransitionPolicy.requireTransition(from, to)`。

- [ ] **Step 1: 添加 KSP、Room、Navigation 和 DataStore 依赖**

在根 `android/build.gradle.kts` 的 `plugins` 中增加：

```kotlin
id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
```

在 `android/app/build.gradle.kts` 的 `plugins` 中增加：

```kotlin
id("com.google.devtools.ksp")
```

在 `dependencies` 中增加：

```kotlin
implementation("androidx.navigation:navigation-compose:2.8.0")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
ksp("androidx.room:room-compiler:2.6.1")
androidTestImplementation("androidx.room:room-testing:2.6.1")
```

- [ ] **Step 2: 写状态转换失败测试**

创建 `TaskTransitionPolicyTest.kt`：

```kotlin
package com.nanzhufeng.videodownloader.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTransitionPolicyTest {
    @Test
    fun waitingCanStartParsingOrSkip() {
        assertTrue(TaskTransitionPolicy.canTransition(DownloadTaskStatus.WAITING, DownloadTaskStatus.PARSING))
        assertTrue(TaskTransitionPolicy.canTransition(DownloadTaskStatus.WAITING, DownloadTaskStatus.SKIPPED))
    }

    @Test
    fun downloadingCanWaitForNetworkOrCancel() {
        assertTrue(TaskTransitionPolicy.canTransition(DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.WAITING_NETWORK))
        assertTrue(TaskTransitionPolicy.canTransition(DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.CANCELLED))
    }

    @Test
    fun terminalStateCannotRestartDirectly() {
        assertFalse(TaskTransitionPolicy.canTransition(DownloadTaskStatus.COMPLETED, DownloadTaskStatus.DOWNLOADING))
        assertFalse(TaskTransitionPolicy.canTransition(DownloadTaskStatus.FAILED, DownloadTaskStatus.DOWNLOADING))
    }
}
```

- [ ] **Step 3: 运行测试并确认失败**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest --tests "*.TaskTransitionPolicyTest"
```

Expected: FAIL，原因是领域类型和 `TaskTransitionPolicy` 尚不存在。

- [ ] **Step 4: 实现领域模型**

`DownloadModels.kt` 至少包含：

```kotlin
package com.nanzhufeng.videodownloader.core.model

enum class DownloadPlatform { YOUTUBE, DOUYIN, TIKTOK }
enum class DownloadSourceKind { SINGLE_VIDEO, CREATOR, CHANNEL, PLAYLIST }
enum class ResolutionPreset { BEST, UP_TO_1080P, UP_TO_720P, AUDIO_MP3 }
enum class DownloadTaskStatus {
    WAITING, PARSING, DOWNLOADING, VALIDATING, PAUSED,
    WAITING_NETWORK, COMPLETED, FAILED, SKIPPED, CANCELLED,
}

val DownloadTaskStatus.isTerminal: Boolean
    get() = this in setOf(COMPLETED, FAILED, SKIPPED, CANCELLED)

data class MediaItem(
    val mediaKey: String,
    val platform: DownloadPlatform,
    val contentId: String,
    val originalUrl: String,
    val sourceKind: DownloadSourceKind,
    val title: String,
    val creator: String,
    val creatorId: String,
    val publishDate: String,
    val thumbnailUrl: String,
)

data class DownloadTask(
    val taskId: String,
    val mediaKey: String,
    val selected: Boolean,
    val sortOrder: Long,
    val resolution: ResolutionPreset,
    val saveTreeUri: String?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadTaskStatus,
    val failureType: String?,
    val errorSummary: String?,
    val retryCount: Int,
    val updatedAt: Long,
)

data class QueuedDownload(
    val task: DownloadTask,
    val media: MediaItem,
)

data class DownloadHistory(
    val taskId: String,
    val platform: DownloadPlatform,
    val contentId: String,
    val originalUrl: String,
    val title: String,
    val creator: String,
    val resolution: ResolutionPreset,
    val finalStatus: DownloadTaskStatus,
    val outputUri: String?,
    val fileSize: Long,
    val fileExists: Boolean,
    val completedAt: Long,
)
```

- [ ] **Step 5: 实现唯一状态转换入口**

`TaskTransitionPolicy.kt`：

```kotlin
package com.nanzhufeng.videodownloader.core.model

object TaskTransitionPolicy {
    private val allowed = mapOf(
        DownloadTaskStatus.WAITING to setOf(
            DownloadTaskStatus.PARSING,
            DownloadTaskStatus.SKIPPED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.PARSING to setOf(
            DownloadTaskStatus.DOWNLOADING,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.SKIPPED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.DOWNLOADING to setOf(
            DownloadTaskStatus.VALIDATING,
            DownloadTaskStatus.PAUSED,
            DownloadTaskStatus.WAITING_NETWORK,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.PAUSED to setOf(
            DownloadTaskStatus.DOWNLOADING,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.WAITING_NETWORK to setOf(
            DownloadTaskStatus.DOWNLOADING,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.VALIDATING to setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
        ),
    )

    fun canTransition(from: DownloadTaskStatus, to: DownloadTaskStatus): Boolean =
        to in allowed[from].orEmpty()

    fun requireTransition(from: DownloadTaskStatus, to: DownloadTaskStatus) {
        require(canTransition(from, to)) { "不允许的任务状态转换：$from -> $to" }
    }
}
```

- [ ] **Step 6: 运行领域测试和现有回归**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*.TaskTransitionPolicyTest"
.\gradlew.bat testDebugUnitTest
```

Expected: 新增测试与现有 22 个 Kotlin 测试全部 PASS。

- [ ] **Step 7: 提交**

```powershell
git add -- android/build.gradle.kts android/app/build.gradle.kts android/app/src/main/java/com/nanzhufeng/videodownloader/core/model/DownloadModels.kt android/app/src/main/java/com/nanzhufeng/videodownloader/core/model/TaskTransitionPolicy.kt android/app/src/test/java/com/nanzhufeng/videodownloader/core/model/TaskTransitionPolicyTest.kt
git commit -m "feat(android): 定义下载任务状态模型"
```

---

### Task 2: Room Schema、DAO 与数据库恢复

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/entity/Entities.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/dao/DownloadDaos.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/NanzhufengDatabase.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/data/database/NanzhufengDatabaseInstrumentedTest.kt`

**Interfaces:**
- Consumes: Task 1 的领域枚举名称，以字符串形式持久化。
- Produces: `MediaItemDao`、`DownloadTaskDao`、`DownloadHistoryDao`、`NanzhufengDatabase`。

- [ ] **Step 1: 写 Room 持久化失败测试**

测试使用 `Room.inMemoryDatabaseBuilder`，插入一个媒体项和一个等待任务，然后验证：

```kotlin
assertEquals(listOf("task-1"), database.downloadTaskDao().observeActive().first().map { it.task.taskId })
database.downloadTaskDao().updateSelection("task-1", false, 200L)
assertFalse(database.downloadTaskDao().getById("task-1")!!.selected)
```

再插入一条 `DownloadHistoryEntity`，验证同一 `platform + contentId + resolution` 可被 `findCompleted` 命中。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

Expected: FAIL，原因是 Room 实体、DAO 和 database 尚不存在。

- [ ] **Step 3: 实现 Room 实体**

`Entities.kt` 定义：

```kotlin
@Entity(
    tableName = "media_items",
    indices = [Index(value = ["platform", "contentId"], unique = true)],
)
data class MediaItemEntity(
    @PrimaryKey val mediaKey: String,
    val platform: String,
    val contentId: String,
    val originalUrl: String,
    val sourceKind: String,
    val title: String,
    val creator: String,
    val creatorId: String,
    val publishDate: String,
    val thumbnailUrl: String,
    val discoveredAt: Long,
)

@Entity(
    tableName = "download_tasks",
    foreignKeys = [ForeignKey(
        entity = MediaItemEntity::class,
        parentColumns = ["mediaKey"],
        childColumns = ["mediaKey"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("mediaKey"), Index("status"), Index("sortOrder")],
)
data class DownloadTaskEntity(
    @PrimaryKey val taskId: String,
    val mediaKey: String,
    val selected: Boolean,
    val sortOrder: Long,
    val resolution: String,
    val saveTreeUri: String?,
    val tempPath: String?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val remainingSeconds: Long?,
    val status: String,
    val failureType: String?,
    val errorSummary: String?,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "download_history",
    indices = [
        Index(value = ["platform", "contentId", "resolution"], unique = true),
        Index("completedAt"),
        Index("creator"),
    ],
)
data class DownloadHistoryEntity(
    @PrimaryKey val taskId: String,
    val platform: String,
    val contentId: String,
    val originalUrl: String,
    val title: String,
    val creator: String,
    val resolution: String,
    val finalStatus: String,
    val outputUri: String?,
    val fileSize: Long,
    val fileExists: Boolean,
    val completedAt: Long,
)
```

同文件增加 Room 关系结果，保证队列一次查询获得标题和博主：

```kotlin
data class DownloadTaskWithMedia(
    @Embedded val task: DownloadTaskEntity,
    @Relation(
        parentColumn = "mediaKey",
        entityColumn = "mediaKey",
    )
    val media: MediaItemEntity,
)
```

- [ ] **Step 4: 实现 DAO**

DAO 必须提供以下公开入口：

```kotlin
@Dao
interface MediaItemDao {
    @Upsert suspend fun upsertAll(items: List<MediaItemEntity>)
    @Query("SELECT * FROM media_items WHERE mediaKey = :mediaKey")
    suspend fun getByKey(mediaKey: String): MediaItemEntity?
}

@Dao
interface DownloadTaskDao {
    @Upsert suspend fun upsertAll(tasks: List<DownloadTaskEntity>)
    @Transaction
    @Query("SELECT * FROM download_tasks WHERE status NOT IN ('COMPLETED','FAILED','SKIPPED','CANCELLED') ORDER BY sortOrder")
    fun observeActive(): Flow<List<DownloadTaskWithMedia>>
    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId")
    suspend fun getById(taskId: String): DownloadTaskEntity?
    @Query("UPDATE download_tasks SET selected = :selected, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun updateSelection(taskId: String, selected: Boolean, updatedAt: Long): Int
    @Query("UPDATE download_tasks SET status = :status, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun updateStatus(taskId: String, status: String, updatedAt: Long): Int
}

@Dao
interface DownloadHistoryDao {
    @Upsert suspend fun upsert(item: DownloadHistoryEntity)
    @Query("SELECT * FROM download_history ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>
    @Query("SELECT * FROM download_history WHERE platform = :platform AND contentId = :contentId AND resolution = :resolution LIMIT 1")
    suspend fun findCompleted(platform: String, contentId: String, resolution: String): DownloadHistoryEntity?
}
```

- [ ] **Step 5: 实现 database version 1**

```kotlin
@Database(
    entities = [MediaItemEntity::class, DownloadTaskEntity::class, DownloadHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NanzhufengDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun downloadHistoryDao(): DownloadHistoryDao
}
```

在 `android/app/build.gradle.kts` 的 Room KSP 配置中把 schema 输出到 `$projectDir/schemas`，并把 `android/app/schemas/` 加入提交。

- [ ] **Step 6: 构建并在模拟器运行 Room 测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am instrument -w -r -e class com.nanzhufeng.videodownloader.data.database.NanzhufengDatabaseInstrumentedTest com.nanzhufeng.videodownloader.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: Room 测试 PASS，任务选择和历史稳定键查询结果准确。

- [ ] **Step 7: 提交**

```powershell
git add -- android/app/src/main/java/com/nanzhufeng/videodownloader/data/database android/app/src/androidTest/java/com/nanzhufeng/videodownloader/data/database android/app/schemas android/app/build.gradle.kts
git commit -m "feat(android): 建立任务与历史数据库"
```

---

### Task 3: Repository、DataStore 与应用组合根

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/DownloadRepository.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepository.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/data/settings/SettingsRepository.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/AppContainer.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/NanzhufengApplication.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepositoryInstrumentedTest.kt`

**Interfaces:**
- Consumes: Room DAO、Task 1 领域模型和 `TaskTransitionPolicy`。
- Produces: `DownloadRepository.activeTasks`、`history`、`enqueue`、`setSelected`、`transition`、`archiveTerminal`；`SettingsRepository.settings`、`setDefaultResolution`、`saveInputDraft`；`AppContainer`。

- [ ] **Step 1: 写 repository 失败测试**

测试依次执行：

1. `enqueue(listOf(media), UP_TO_720P)` 后 `activeTasks.first()` 返回一个默认选中的 `WAITING` 任务。
2. `setSelected(taskId, false)` 后任务仍存在但未选中。
3. `transition(taskId, DOWNLOADING)` 从 `WAITING` 直接转换时抛出异常。
4. `transition(taskId, PARSING)` 后再到 `DOWNLOADING` 成功。
5. 终态 `COMPLETED` 归档后，`history.first()` 返回相同作品稳定键。

- [ ] **Step 2: 运行测试并确认失败**

Run: `.\gradlew.bat assembleDebugAndroidTest`

Expected: FAIL，原因是 repository 尚不存在。

- [ ] **Step 3: 定义 repository 接口**

```kotlin
interface DownloadRepository {
    val activeTasks: Flow<List<QueuedDownload>>
    val history: Flow<List<DownloadHistory>>
    suspend fun enqueue(items: List<MediaItem>, resolution: ResolutionPreset): List<String>
    suspend fun setSelected(taskId: String, selected: Boolean)
    suspend fun transition(taskId: String, to: DownloadTaskStatus)
    suspend fun archiveTerminal(history: DownloadHistory)
}
```

`RoomDownloadRepository` 的 `enqueue` 使用 `database.withTransaction` 同时写媒体和任务；任务 ID 使用 `UUID.randomUUID().toString()`，`mediaKey` 固定为 `${platform.name}:${contentId}`。`transition` 必须先读取当前状态并调用 `TaskTransitionPolicy.requireTransition`，页面不得直接调用 DAO 更新状态。

- [ ] **Step 4: 实现 DataStore 设置**

```kotlin
data class AppSettings(
    val defaultResolution: ResolutionPreset = ResolutionPreset.UP_TO_720P,
    val customTreeUri: String? = null,
    val inputDraft: String = "",
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setDefaultResolution(value: ResolutionPreset)
    suspend fun saveInputDraft(value: String)
}
```

Preferences keys 固定为 `default_resolution`、`custom_tree_uri`、`input_draft`。未知枚举值回退到 `UP_TO_720P`，不能让设置损坏导致应用启动失败。

- [ ] **Step 5: 创建 AppContainer 和 Application**

`NanzhufengApplication` 必须继承 `com.chaquo.python.android.PyApplication`，保留 Chaquopy 初始化：

```kotlin
class NanzhufengApplication : PyApplication() {
    val container: AppContainer by lazy { AppContainer.create(this) }
}
```

`AppContainer.create(context)` 创建 `Room.databaseBuilder(..., "nanzhufeng-video-downloader.db")`、`RoomDownloadRepository` 和 `PreferencesSettingsRepository`。Manifest 的 `android:name` 改为 `.NanzhufengApplication`。

- [ ] **Step 6: 运行 repository 仪器测试**

使用 Task 2 相同的模拟器安装方式，仅运行 `RoomDownloadRepositoryInstrumentedTest`。

Expected: 队列、选择、合法/非法状态转换和历史归档全部 PASS。

- [ ] **Step 7: 提交**

```powershell
git add -- android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository android/app/src/main/java/com/nanzhufeng/videodownloader/data/settings android/app/src/main/java/com/nanzhufeng/videodownloader/AppContainer.kt android/app/src/main/java/com/nanzhufeng/videodownloader/NanzhufengApplication.kt android/app/src/main/AndroidManifest.xml android/app/src/androidTest/java/com/nanzhufeng/videodownloader/data/repository
git commit -m "feat(android): 持久化队列历史与基础设置"
```

---

### Task 4: 主题、响应式导航与三页壳层

**Files:**
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui/NanzhufengTheme.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/AppDestination.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/navigation/NanzhufengApp.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/HomeScreen.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/history/HistoryScreen.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreen.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/MainActivity.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeScreen.kt`
- Test: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation/NanzhufengAppInstrumentedTest.kt`

**Interfaces:**
- Consumes: `DownloadRepository` 与 `SettingsRepository` 的 `Flow`，现有 `ProbeScreen` 和 `DouyinProbeActivity`。
- Produces: `NanzhufengApp(container, onOpenDouyin)`、`HomeScreen`、`HistoryScreen`、`SettingsScreen`。

- [ ] **Step 1: 写导航和响应式失败测试**

Compose 测试使用稳定 test tag：

```kotlin
onNodeWithTag("home-screen").assertIsDisplayed()
onNodeWithTag("nav-history").performClick()
onNodeWithTag("history-screen").assertIsDisplayed()
onNodeWithTag("nav-settings").performClick()
onNodeWithTag("settings-screen").assertIsDisplayed()
onNodeWithTag("probe-entry").performClick()
onNodeWithTag("probe-screen").assertIsDisplayed()
```

分别用窄宽和 `840.dp` 以上宽度验证 `bottom-navigation` 与 `navigation-rail` 只显示其中一个。

- [ ] **Step 2: 运行测试并确认失败**

Run: `.\gradlew.bat assembleDebugAndroidTest`

Expected: FAIL，原因是三页应用壳层尚不存在。

- [ ] **Step 3: 实现主题 token**

`NanzhufengTheme.kt` 固定：

```kotlin
val PrussianBlue = Color(0xFF0D3A69)
val HermesOrange = Color(0xFFEB5C20)
val MarsGreen = Color(0xFF018B8D)
val WorkspaceBackground = Color(0xFFF4F7FB)
val ContentSurface = Color(0xFFFFFFFF)
val WaitingYellow = Color(0xFFF9D46C)
val FailureRed = Color(0xFFC8161D)
```

Material `primary = PrussianBlue`、`secondary = HermesOrange`、`tertiary = MarsGreen`，组件圆角不超过 `8.dp`。不加入渐变、装饰光斑或卡片嵌套。

- [ ] **Step 4: 实现导航**

`AppDestination` 固定为 `HOME`、`HISTORY`、`SETTINGS`、`PROBE`。`NanzhufengApp` 使用 `BoxWithConstraints`：

- `maxWidth < 840.dp`：`Scaffold` 底部 `NavigationBar`。
- `maxWidth >= 840.dp`：左侧 `NavigationRail`，右侧内容区。
- `PROBE` 不出现在一级导航，只由设置页和首页过渡入口打开。

`MainActivity` 从 `application as NanzhufengApplication` 获取 container，不在 Activity 创建 database。

- [ ] **Step 5: 实现首页壳层**

首页顺序固定为：当前下载、队列、总进度、3 行链接输入、通栏智能读取。输入草稿来自 `SettingsRepository` 并在失焦或点击智能读取时保存。当前没有正式 discovery adapter 时，“智能读取”把输入传给 `ProbeScreen(initialInput = input)`，不能在首页复制 yt-dlp 或抖音解析逻辑。

队列必须直接显示 `DownloadRepository.activeTasks`；空数据库显示简洁空状态，不注入演示数据。所有状态文字和数值居中，标题和博主保留可读文本区。

- [ ] **Step 6: 实现历史和设置壳层**

历史页直接观察 `DownloadRepository.history`，显示搜索输入和状态筛选外观；本阶段只实现本地列表、空状态和记录详情，不实现删除文件。

设置页实现：

- 默认分辨率单选：最佳画质、1080p 及以下、720p 及以下、仅音频 MP3。
- 默认目录说明：`Movies/南烛枫视频下载器/平台/博主名/`。
- “技术验证”入口，打开 `ProbeScreen`。
- 内容边界：支持公开内容和登录后账号有权限访问的内容，不绕过会员、付费、DRM 或私密内容。

- [ ] **Step 7: 调整 ProbeScreen 过渡参数**

把签名改为：

```kotlin
@Composable
fun ProbeScreen(
    initialInput: String = DEFAULT_YOUTUBE_PROBE_URL,
    onOpenDouyin: (String) -> Unit,
    viewModel: ProbeViewModel = viewModel(),
)
```

输入状态使用 `rememberSaveable(initialInput) { mutableStateOf(initialInput) }`，根节点添加 `Modifier.testTag("probe-screen")`。不修改探针下载行为。

- [ ] **Step 8: 运行 Compose 测试与完整回归**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Expected: 新增导航测试可编译；现有 URL、下载器、TikTok、抖音探针测试无回归。

- [ ] **Step 9: 提交**

```powershell
git add -- android/app/src/main/java/com/nanzhufeng/videodownloader/core/ui android/app/src/main/java/com/nanzhufeng/videodownloader/navigation android/app/src/main/java/com/nanzhufeng/videodownloader/feature android/app/src/main/java/com/nanzhufeng/videodownloader/MainActivity.kt android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProbeScreen.kt android/app/src/androidTest/java/com/nanzhufeng/videodownloader/navigation
git commit -m "feat(android): 搭建响应式三页应用壳层"
```

---

### Task 5: 模拟器与 Find N5 最小验收文档

**Files:**
- Create: `docs/verification/android-room-shell-result.md`
- Modify: `docs/superpowers/specs/2026-07-15-android-find-n5-design.md`

**Interfaces:**
- Consumes: Tasks 1-4 的 Room、repository、DataStore 和三页 UI。
- Produces: 可复核的构建、模拟器、Find N5 外/内屏和数据恢复结果。

- [ ] **Step 1: 运行完整自动测试**

Run:

```powershell
$env:PYTHONPATH='android/app/src/main/python'
python -m unittest discover -s android/app/src/test/python -p 'test_*.py'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\android\gradlew.bat -p .\android clean testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Expected: Python、Kotlin、Room 和 Compose 测试全部 PASS，两个 APK 生成。

- [ ] **Step 2: 模拟器验证数据库恢复与导航**

覆盖安装 APK 和测试 APK，运行 Room repository 与导航仪器测试。验证 Activity 重建后输入草稿、队列和历史仍来自 DataStore/Room，而不是 `remember` 内存。

- [ ] **Step 3: Find N5 外屏验收**

覆盖安装，不卸载、不清数据。验证：

- 底部导航为首页、历史、设置。
- 首页输入框约 3 行，只有一个“智能读取”。
- 智能读取能把当前输入传入技术探针。
- 设置页可调整默认分辨率，重启后保持。
- 现有 YouTube/抖音/TikTok 探针仍能打开。

保存 1140 × 2616 截图到 `docs/assets/android/room-shell-find-n5-outer.png`。

- [ ] **Step 4: Find N5 内屏验收**

展开设备，验证左侧 NavigationRail、主内容区和安全间距；折叠/展开后当前一级页面和输入草稿不丢失。保存 2248 × 2480 截图到 `docs/assets/android/room-shell-find-n5-inner.png`。

- [ ] **Step 5: 写验证结果**

`android-room-shell-result.md` 必须区分：

```markdown
- 已实现：Room、repository、DataStore、三页导航和过渡探针入口。
- 仅测试通过：列测试数量和命令。
- 已在 Find N5 真实验证：列外屏、内屏、折叠切换和设置恢复结果。
- 待验证风险：后台下载、网络恢复、登录和正式智能读取仍未实现。
```

同时把总设计文档状态从“Android 代码尚未开始”更新为“可行性验证已通过，Room 与三页壳层已实现”，不改写尚未实现的功能状态。

- [ ] **Step 6: 最终检查**

Run:

```powershell
git diff --check
git status --short
```

Expected: 只包含本阶段预期文档和截图；APK、数据库和测试下载文件未被跟踪。

- [ ] **Step 7: 提交**

```powershell
git add -- docs/verification/android-room-shell-result.md docs/superpowers/specs/2026-07-15-android-find-n5-design.md docs/assets/android/room-shell-find-n5-outer.png docs/assets/android/room-shell-find-n5-inner.png
git commit -m "test(android): 验证 Room 与三页壳层"
```

---

## Stop Conditions

出现以下任一情况立即停止，不继续后台下载或完整业务接入：

1. Room 队列在 Activity 重建或折叠/展开后丢失。
2. Application 改为 `NanzhufengApplication` 后 Chaquopy 无法初始化。
3. 三页界面自己维护任务终态，无法以 repository `Flow` 为唯一数据来源。
4. 外屏或内屏出现导航、输入框和关键操作重叠，且不能通过 `840.dp` 响应式边界修复。
5. 为实现壳层需要清除 Find N5 应用数据、申请所有文件访问权限或破坏现有真实下载探针。

## Next Phase Boundary

本计划完成后，下一份计划只接入“公开单视频智能读取 -> Room 队列 -> 单任务下载”；批量读取、后台 Worker、登录、网络恢复和自定义目录继续按后续独立阶段实现。
