# 南枫下载 Android 完整开发档案

更新时间：2026-08-23（Asia/Shanghai）
审阅范围：当前 `main`、105 个 Git 提交、Android/旧桌面源码、构建配置、Room schema、测试目录、项目文档和现有验证记录。本文只归纳已观察到的事实；外部平台状态与设备结果不作推断。

## 1. 项目定位

南枫下载的当前维护对象是 Android 下载工作台，应用 ID 为 `com.nanzhufeng.videodownloader`。它处理 YouTube、抖音、TikTok、哔哩哔哩和小红书的公开、合规可访问媒体，目标闭环是：读取链接、去重入队、下载或处理、写入 MediaStore/用户选择目录，并在历史中再次打开。

根目录仍保留 PySide 桌面原型（`app/`、`start.py`、`run.ps1` 与桌面打包文件）。它是 2026-07-15 初始化时的实现，不是当前 Android 构建入口；当前产品能力、版本和验收均以 `android/` 为准。

产品边界由 `README.md`、`UrlClassifier.kt` 和会话实现共同约束：不以会员、付费、DRM、私密内容或地区限制绕过为目标；平台受账号、地区、CDN 和反爬变化影响时，必须保留真实失败状态。

## 2. 当前现场快照

| 项目 | 现场事实 | 依据 |
| --- | --- | --- |
| 分支 / 最近提交 | `main` / `dcfe85a` | Git HEAD |
| Android 版本 | `1.2.82` / `10282` | `android/app/build.gradle.kts` |
| SDK / JVM | minSdk 29、compile/targetSdk 35、Java/Kotlin 17 | `android/app/build.gradle.kts` |
| 数据库 | Room schema v9，显式 1→9 迁移 | `NanzhufengDatabase.kt`、`android/app/schemas/` |
| 正式 APK | `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.82.apk` | v1.2.82 验证记录 |
| APK SHA-256 | `758e2cf7260f90501d5dbfb3be1fd4c0f92eb9d116e665bfa8945183e1aac6b7` | v1.2.82 验证记录 |

当前工作树在本档案开始时已有两项未跟踪本地文件：`.playwright-cli/` 与 `docs/2026-08-23-douyin-gallery-watermark-claude-brief.md`。后者是被明确废弃的求解简报，不是当前方案或验收事实源。

## 3. 技术栈与构建

| 层 | 当前实现 |
| --- | --- |
| Android UI | Kotlin、Jetpack Compose、Material 3、Navigation Compose；单 `MainActivity` |
| 异步与后台 | Coroutines、Lifecycle、WorkManager 前台 dataSync Worker |
| 持久化 | Room 2.6.1、DataStore Preferences |
| 网络与发现 | OkHttp、Chaquopy 17.0.0、Python 3.13、固定 `yt-dlp==2026.8.19` 及 hash |
| 媒体 | Media3 1.7.1、MediaExtractor/MediaCodec、MediaStore、Coil GIF/Video |
| 原生音频 | NDK 26.1.10909125、CMake 3.22.1、随仓库 LAME 4.0、JNI 流式 MP3 编码 |
| ABI | `arm64-v8a`、`x86_64` |
| 构建工具 | Gradle 8.7、AGP 8.6.1、Kotlin/KSP 2.0.20 |

Release 签名只接受完整 `NANFENG_RELEASE_*` 环境变量，或用户级 `nanzhufengDownload.release.*` 后备值；若只提供一部分，Gradle 直接失败。`stageFormalReleaseArtifacts` 会从已签名的 Release APK/AAB 生成版本化正式产物。

## 4. 目录与模块

```text
android/app/src/main/
├── java/com/nanzhufeng/videodownloader/
│   ├── core/        领域模型、状态机、主题、共享 UI、中文错误
│   ├── data/        Room、DAO、Repository、DataStore、网络状态
│   ├── domain/      发现、会话、下载、传输、转码、输出
│   ├── feature/     首页、历史、设置及内置播放器
│   ├── navigation/  三页导航与根层视频播放会话
│   └── probe/       链接分类、Chaquopy 桥、抖音页面捕获、HTTP 下载
├── python/nanzhufeng_probe/  yt-dlp 和平台页面解析
├── cpp/             LAME 构建、JNI、第三方源码
├── res/             图标、主题、动画、XML 规则
├── test/             JVM/MockWebServer/契约测试
└── androidTest/      历史仪器测试源码与夹具（永久不执行）
```

`AppContainer` 是应用组合根，显式装配 Room、会话、发现、任务运行器、MediaStore 输出、WorkManager 调度和网络状态。`NanzhufengApplication` 持有该容器，`MainActivity` 只承载 Compose 根。

## 5. 核心数据模型与数据流

```text
分享 Intent / 首页粘贴
  → UrlClassifier + PlatformSourceDiscoveryEngine
  → Chaquopy yt-dlp 或受限目标页 WebView 捕获
  → MediaItem + DownloadTask（Room）
  → DefaultDownloadEngine + WorkManagerDownloadScheduler
  → ForegroundDownloadWorker + DownloadTaskRunner
  → YtDlpTaskMediaResolver + DirectMediaTransfer
  → 合并 / 转码 / 分段 / 内容校验
  → MediaStoreOutputStore 或 SAF 目录
  → DownloadHistory + 吞吐报告 → 首页 / 通知 / 历史 / 播放器
```

核心表为 `media_items`、`download_tasks`、`download_history` 与 `download_throughput_reports`。`DownloadRepository` 是 UI 与任务层唯一的数据边界；`TaskTransitionPolicy` 限制等待、解析、下载、校验、暂停、网络等待与终态之间的合法转换。历史既记录最终 URI，也记录多分段/图集 URI、媒体是否仍可读及失败原因。

## 6. 核心模块职责

| 模块 | 职责与关键约束 |
| --- | --- |
| `PlatformSourceDiscoveryEngine` | 分类、短链规范、单作品/合集读取；合集按作者 ID 过滤和去重。抖音 `/note/` 不允许通用解析绕过目标页捕获。 |
| `DouyinProbeActivity` / `DouyinCaptureStore` | 只从当前作品 React Flight 的 `aweme.detail.images[].urlList` 接收完整图片列表；声明数、HTTPS、去重和 `tplv-dy-water` 排除缺一不可。 |
| `RoomDownloadRepository` | 入队去重、旧图源刷新、任务恢复、状态写入、历史重试和缺失媒体清理。 |
| `ForegroundDownloadWorker` | 网络约束下启动有限并行 lane，进程恢复后继续可运行任务，并以持久队列生成前台通知。 |
| `YtDlpTaskMediaResolver` | 已持久化的合格抖音图集优先于临时缓存和 yt-dlp；普通媒体验证解析 ID 与任务 ID 一致。 |
| `DirectMediaTransfer` / `HttpFileDownloader` | Range、多连接、续传、重试、吞吐报告、音视频处理；图集需要逐项通过真实媒体校验。无请求 `206` 需续传到声明长度，空体/截断 WebP/水印重定向均失败。 |
| `MediaStoreOutputStore` | 用 `IS_PENDING` 或 DocumentsUI 创建输出，复制后复验；任一失败清理本次新建 URI/文档，不覆盖既有文件。 |
| `feature/history` | 本地存在性、媒体类型、可打开性分别判断；图片/动图/视频/音频走合适的 App 内或外部入口。 |
| `navigation/NanzhufengApp` | 在根层保存视频 taskId、URI、位置和播放态，避免页面或折叠变化使局部播放器覆盖新会话。 |

## 7. 关键决策及原因

1. **持久化队列而非 UI 临时状态。** Room 保存媒体、任务、历史和吞吐报告，Worker 可在进程恢复后调用 `recoverInterruptedTasks`；这是避免“页面显示成功但后台状态丢失”的基础。
2. **发现、会话、传输分层。** yt-dlp/Python 只负责受控解析；会话按站点与目标主机作用域提供；最终字节传输由 Kotlin 的可恢复下载器负责，防止凭据被普通请求头或日志泛化。
3. **来源契约优于泛化成功。** 抖音图文历史上可得到水印变体，因此 `/note/` 必须取目标页的完整 `urlList`，并且任务解析阶段不能再以 yt-dlp 成功结果覆盖。
4. **媒体完整性优于 HTTP 成功。** 当前传输链针对提前 `206`、重定向、空响应、分段中断和 WebP RIFF 长度不匹配失败关闭；发布前和发布后都复核内容。
5. **输出采用临时/可清理写入。** MediaStore 使用 pending 标记，SAF 保存路径逐级创建；失败删除本轮新对象而不碰既有用户媒体。
6. **MP3 是真实编码链。** Android 解码成 PCM 后调用 JNI LAME 写入 MPEG Layer III，不把源容器改后缀当作 MP3。
7. **折叠屏播放会话提升到根层。** `NanzhufengApp` 而非 History destination 拥有播放状态，以避免配置变化、导航和旧回调造成错播。

## 8. 开发过程与重要里程碑

| 时间 / Git | 已完成的增量 |
| --- | --- |
| 2026-07-15 `4077045` | 建立 PySide 原型与最初抖音浏览器捕获实验。 |
| 2026-07-16 `cee7de0` | 建立 Android/Chaquopy 可行性工程。 |
| 2026-07-16 `8b8d729` | 引入 Room、任务状态机、DataStore 与 Home/History/Settings 三页壳。 |
| 2026-07-16 至 18 | 严格发现、队列、恢复下载、MP3 原生编码、折叠屏工作台和正式签名链逐步落地。 |
| 2026-07-21 `2b8db69` | 发布 Android 正式工作台；后续 v1.0.0 验证记录固定签名与模拟器产物证据。 |
| 2026-07-26 `5d7f72e` | 扩展哔哩哔哩、小红书及对应会话/解析/输出策略。 |
| 2026-07-27 `4fe4db8` 至 `8f851e4` | 音频回退、内置播放、长音频/视频分段、历史批量删除与快速合并。 |
| 2026-08-02 `457ee62` | 修复抖音短链进入 Generic 解析导致无格式的问题，并补目标页捕获的后备链。 |
| 2026-08-21 `1bdcc11`、`69a313b` | 交付 1.2.48 可靠性/播放器修复，强化本地历史媒体与根播放层。 |
| 2026-08-22 `8377cb4`、`8149cac` | 图集、媒体格式、保存进度、旧源刷新、图文结构化来源等持续修复。 |
| 2026-08-23 `dcfe85a` | 1.2.82 固定 `/note/` 来源、CDN `206` 续传、空/截断 WebP 拒绝、最终重定向水印拒绝和抖音图集顺序传输。 |

## 9. 踩坑与已实施修复

| 问题 | 根因 | 现有修复与边界 |
| --- | --- | --- |
| 抖音短链解析失败 | `v.douyin.com` 未先受控规范，落入 Generic 解析 | Python 只接受跳向官方域的短链；历史 v1.2.8 已有 OPPO 单视频闭环。 |
| 抖音图文少图/水印 | 首图、`downloadUrlList` 或 yt-dlp 可替换目标页原图列表 | `/note/` 强制目标页 React Flight，精确数量与水印 URL 门禁；来源门禁不等于像素证明。 |
| 14 图 CDN 空/截断响应 | 多个签名图请求并发和非请求 `206` 可能得到不完整字节 | 抖音图集顺序传输、`206` 续传、空体/RIFF 长度/最终 URL 校验；失败不发布。 |
| “正在校验”看似卡死 | 实际耗时在 MediaStore/SAF 复制，旧 UI 状态不诚实 | 复制按真实字节更新发布进度，完成前不显示 100%。 |
| 历史打不开被误报播放器问题 | 文件存在、类型、可读性和播放器可用性被混成一个状态 | 历史先读本地 URI，再按类型选择内部/外部播放器，并保留缺失状态。 |
| 真实 MP3 不可靠 | 仅改扩展名或依赖不确定源格式不足以保证可播 | PCM→LAME、输出内容校验与原生/仪器历史测试。 |

## 10. 测试、验证与部署

### 自动与隔离验证

- 当前测试源码包括 47 个 JVM Kotlin 测试文件、19 个 `androidTest` 源文件和 3 个 Python 测试文件；覆盖状态机、Repository、解析、HTTP Range/续传、媒体校验、输出策略、UI 契约、会话、转码和分段。
- `androidTest` 源文件是历史资产，但 `android/app/build.gradle.kts` 对所有 `connected*AndroidTest` 设为失败关闭，永久不得运行；尤其禁止向 OPPO 部署 Debug/仪器包。
- 1.2.82 checkpoint 已执行 Debug/Release JVM 单测和 Debug/Release lint，结果 `BUILD SUCCESSFUL`。该证据只说明代码门禁。
- 隔离模拟器通过正常 UI 路径完成抖音图文样本 2/2 与 14/14 的 MediaStore 输出；前者两张已人工确认无抖音号水印，后者 14 张逐张 `ffmpeg` 可解码，但没有把后者写成逐张人工视觉判定。

### OPPO 与正式包

- 1.2.82 正式 APK 已在 OPPO Find N5 从 1.2.81 同签名覆盖，首次安装时间及 CE/DE inode 保持，回拉 `base.apk` 与本地产物 hash 一致。
- 覆盖后用户已确认 1.2.82 完成 OPPO 抖音图文真实下载闭环，14 张样本也已逐张人工视觉确认无水印；现有项目记录未保存新的设备命令、MediaStore URI 或逐图尺寸，因此不补写这些细节。
- 正式覆盖前必须读取已装版本、`DEBUGGABLE`、证书和数据指纹；签名不同或版本不递增时停止，绝不卸载或清数据。

## 11. 文档冲突与处理口径

| 文档 | 与现场冲突 | 处理 |
| --- | --- | --- |
| `README.md` | 写为开发版 1.2.67、发布版 1.2.48，构建产物示例为 1.2.8。 | 这些是面向访客的旧状态；当前版本、产物和验证看 Gradle、交接和验证记录。 |
| `context.md` | 写为 1.2.8、数据库 v6、AGP 8.5.2、旧分支。 | 与当前 Gradle 的 1.2.82、Room v9、AGP 8.6.1 不一致，只可作历史架构参考。 |
| `docs/nanzhufeng-video-downloader-development-context-for-chatgpt.md` 与 `PROJECT_HANDOFF.md` | 已更新到 1.2.82，但仍保留早期里程碑段落。 | 当前增量优先看顶部最新段和 `docs/verification/2026-08-23-android-v1.2.82-douyin-gallery-integrity.md`。 |
| 未跟踪 Claude 简报 | 基于 1.2.81 且包含已废弃方向。 | 不纳入任何当前结论或提交。 |

## 12. 已知问题与后续路线

1. 将 README 与 `context.md` 作为独立文档同步任务更新，避免继续传播旧版本、构建与数据库事实；更新前仍需重读现场配置。
2. 对每个外部平台以新的公开样本独立确认读取、下载、输出和再次打开。历史成功不可推断为平台当前可用。
3. 若修改数据库、会话、媒体输出或来源策略，先补相应迁移/契约测试，再安排隔离模拟器与经授权的正式设备验收。

## 13. 证据入口

- 当前动态事实：`PROJECT_HANDOFF.md`、`docs/verification/`、Gradle 与源码。
- 完整历史：`git log --all --reverse --format='%h|%ad|%s'`（审阅时共 105 提交）。
- Android 设计/执行史：`docs/superpowers/plans/` 与 `docs/verification/`；它们是历史证据，不取代现场代码。
