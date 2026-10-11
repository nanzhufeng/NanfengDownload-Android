# 南枫下载 Android 完整开发档案

初版：2026-08-23；网站范围增量更新：2026-10-08；146合同与经验增量：2026-10-11（Asia/Shanghai）
初版审阅范围：当时 `main` 的 105 个 Git 提交、Android/旧桌面源码、构建配置、Room schema、测试目录、项目文档和现有验证记录。后续增量以当前代码和对应日期的验证记录为准；外部平台状态与设备结果不作推断。

## 1. 项目身份与定位

南枫下载的当前维护对象是 Android 下载工作台，应用 ID 为 `com.nanzhufeng.videodownloader`。既有 YouTube、抖音、TikTok、哔哩哔哩和小红书之外，当前本地代码已接入 Pornhub 单作品与通用 HTTP(S) 网页/媒体/合集，但不能把入口代码等同于这些网站的实站验收。目标闭环是：读取链接、去重入队、下载或处理、写入 MediaStore/用户选择目录，并在历史中再次打开。

根目录仍保留 PySide 桌面原型（`app/`、`start.py`、`run.ps1` 与桌面打包文件）。它是 2026-07-15 初始化时的实现，不是当前 Android 构建入口；当前产品能力、版本和验收均以 `android/` 为准。

产品边界由 `README.md`、`UrlClassifier.kt` 和会话实现共同约束：不以会员、付费、DRM、私密内容或地区限制绕过为目标；平台受账号、地区、CDN 和反爬变化影响时，必须保留真实失败状态。

## 2. 当前源码快照（2026-10-11）

| 项目 | 现场事实 | 依据 |
| --- | --- | --- |
| 分支 / 验证基线 | `main`；生产代码 `d0602d13c87411f5225889c04ef9a6f403a2a362`，本次文档增量前 HEAD `110ccb5e2a509f4d002b3ac2ef321d1480d4e8ad` | Git与146生产文件清单；本地检查点见状态/交接 |
| Android 版本 | `1.2.146` / `10346` | `android/app/build.gradle.kts` |
| SDK / JVM | minSdk 29、compile/targetSdk 35、Java/Kotlin 17 | `android/app/build.gradle.kts` |
| 数据库 | Room schema v11，显式 1→11 迁移 | `NanzhufengDatabase.kt`、`android/app/schemas/` |
| 本地签名 Release 构建 | 146正式包及隔离模拟器结果看发布验证；OPPO仅上轮授权覆盖142 | `docs/verification/2026-10-11-release-146.md`、`docs/verification/2026-10-11-oppo-release-142-overlay.md` |

本档案初版的工作树快照属于 2026-08-23 历史；2026-10-08 的工作区还包含先前抖音、设置等未提交修改及本次网站范围代码，不得把本档案中的旧版构建/安装记录当作现装状态。

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

执行合同要求完整 `NANFENG_RELEASE_*` 环境变量优先，用户级 `nanzhufengDownload.release.*` 完整组作备用；当前Gradle实际按字段回退并仅检查最终四字段齐全，未自动保证来源整组选择。这一配置门禁缺口记录于本次审计，不能宣称部分环境变量一定失败。本轮冻结包身份已有独立证书证据，未修改生产构建配置。`stageFormalReleaseArtifacts` 会从已签名的 Release APK/AAB 生成版本化正式产物。

## 4. 目录结构与模块

```text
android/app/src/main/
├── java/com/nanzhufeng/videodownloader/
│   ├── core/        领域模型、状态机、主题、共享 UI、中文错误
│   ├── data/        Room、DAO、Repository、DataStore、网络状态
│   ├── domain/      发现、会话、下载、传输、转码、输出
│   ├── feature/     首页、历史、设置及内置播放器
│   ├── navigation/  三页导航与根层视频播放会话
│   └── probe/       链接分类、Chaquopy 桥、受限网页捕获、HTTP/分段下载
├── python/nanzhufeng_probe/  yt-dlp、平台/通用网页/合集解析与分段传输
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
  → Chaquopy yt-dlp 或受限目标页 WebView 捕获；多 URL/合集由用户勾选
  → MediaItem + DownloadTask（Room）
  → DefaultDownloadEngine + WorkManagerDownloadScheduler
  → ForegroundDownloadWorker + DownloadTaskRunner
  → YtDlpTaskMediaResolver + DirectMediaTransfer / ProtocolFileDownloader
  → 合并 / 转码 / 分段 / 内容校验
  → MediaStoreOutputStore 或 SAF 目录
  → DownloadHistory + 吞吐报告 → 首页 / 通知 / 历史 / 播放器
```

核心表为 `media_items`、`download_tasks`、`download_history` 与 `download_throughput_reports`。`DownloadRepository` 是 UI 与任务层唯一的数据边界；`TaskTransitionPolicy` 限制等待、解析、下载、校验、暂停、网络等待与终态之间的合法转换。历史既记录最终 URI，也记录多分段/图集 URI、媒体是否仍可读及失败原因。

历史视频收藏以单个历史行的 `isFavorite` 为事实源，Repository负责更新、归档保留及删除联动；入口、类型门禁、组合筛选及生命周期见 `history-favorites.md`。

## 6. 核心模块职责

| 模块 | 职责与关键约束 |
| --- | --- |
| `PlatformSourceDiscoveryEngine` | 分类、短链规范、单作品/合集读取；合集按作者 ID 过滤和去重。抖音 `/note/` 不允许通用解析绕过目标页捕获。 |
| `probe`读取接口 / `android_network.py` | 官方轻量API/SSR只负责目标身份与元数据，不制造传输URL；可选信息缺失留未知，Generic完整快照仅同次同来源复用。Python遵循Android现有HTTP代理，主机路由保持原设置；不改环境变量，不硬编码地址或关闭TLS。 |
| `DouyinProbeActivity` / `DouyinCaptureStore` | 只从当前作品 React Flight 的 `aweme.detail.images[].urlList` 接收完整图片列表；声明数、HTTPS、去重和 `tplv-dy-water` 排除缺一不可。 |
| `RoomDownloadRepository` | 入队去重、旧图源刷新、任务恢复、状态写入、历史重试和缺失媒体清理。 |
| `ForegroundDownloadWorker` | 按实际网络预检启动有限并行 lane，不以系统联网验证波动反复取消；进程恢复后继续可运行任务，并以持久队列生成前台通知。 |
| `YtDlpTaskMediaResolver` | 已持久化的合格抖音图集优先于临时缓存和 yt-dlp；普通媒体验证解析 ID 与任务 ID 一致。 |
| `ForegroundDownloadWorker` / `DownloadIslandState` / `DownloadIslandOverlay` | 唯一后台队列的真实状态同时驱动通知与简洁/详细进度岛；实际Activity可见、锁屏或非交互隐藏，窗口异常不取消下载；停止经DownloadEngine，任务/历史仍归Repository。WorkManager管理已有前台唤醒锁及系统停止/恢复，详见`background-download-island.md`。 |
| `DirectMediaTransfer` / `HttpFileDownloader` / `Media3TrackMuxer` / `AndroidStreamRemuxer` | Range、多连接预算、旧断点布局、HLS/DASH提交游标、TS排空和压缩样本封装的唯一合同见[共享视频传输策略](download-transfer-policy.md)。独立轨及TS路径使用Media3封装，TS仍由MediaExtractor提取，禁止隐式重编码；失败/取消保来源。图集逐项校验真实媒体，无请求 `206` 续传到声明长度，空体/截断 WebP/水印重定向均失败。 |
| `MediaStoreOutputStore` | 用 `IS_PENDING` 或 DocumentsUI 创建输出，复制后复验；任一失败清理本次新建 URI/文档，不覆盖既有文件。 |
| `MediaFileValidator` | 共享媒体结构预检；小MP4/WebM按有界容器结构判定，不只凭体积或裸签名。MediaStore索引纳入小文件候选，仍经同一检查。结构预检不替代真实样本完整解码。 |
| `feature/history` | 本地存在性、媒体类型、可打开性分别判断；图片/动图/视频/音频走合适的 App 内或外部入口。 |
| `navigation/NanzhufengApp` / `domain/playback` / `InternalVideoPlayerDialog` | 根层拥有播放会话，按成品URI持久保存位置，每个会话绑定独立PlayerView及旧回调；左右连续跳转与原生按钮统一10秒，详见`video-playback.md`。 |
| `core/ui/IslandFeedback` | 应用内临时反馈的单一渲染器；不抢焦点、保持完整文字、按生命周期清理。完成提示只观察历史真实成品，不写入任务状态。视觉与字体权威正文见`design/2026-10-10-island-feedback-ui-contract.md`。 |

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
| 2026-09-23 至 10-08，本地未提交 | 抖音目标页下载修复后，按网站范围方案实现 Pornhub 分类、通用网页/媒体/合集、分段协议、动态播放器捕获和 Room v10；受控 Android 模拟器及 MDN 公开直链/交互式演示页验收见 2026-10-08 验证记录。 |
| 2026-10-09，本地未提交 | 官方 Pornhub 单作品与其 CDN 使用限定兼容请求，修复 Android 嵌入 yt-dlp 的 410；剥除 AAC ADTS 头后，隔离 AVD 对一个公开作品完成正常 UI 下载、MediaStore 回读和内置播放观察。内外屏共用悬浮导航缩小并保留手势安全区。证据和边界见 2026-10-09 验证记录。 |

## 9. 踩坑与已实施修复

| 问题 | 根因 | 现有修复与边界 |
| --- | --- | --- |
| 抖音短链解析失败 | `v.douyin.com` 未先受控规范，落入 Generic 解析 | Python 只接受跳向官方域的短链；历史 v1.2.8 已有 OPPO 单视频闭环。 |
| 抖音图文少图/水印 | 首图、`downloadUrlList` 或 yt-dlp 可替换目标页原图列表 | `/note/` 强制目标页 React Flight，精确数量与水印 URL 门禁；来源门禁不等于像素证明。 |
| 14 图 CDN 空/截断响应 | 多个签名图请求并发和非请求 `206` 可能得到不完整字节 | 抖音图集顺序传输、`206` 续传、空体/RIFF 长度/最终 URL 校验；失败不发布。 |
| “正在校验”看似卡死 | 实际耗时在 MediaStore/SAF 复制，旧 UI 状态不诚实 | 复制按真实字节更新发布进度，完成前不显示 100%。 |
| 历史打不开被误报播放器问题 | 文件存在、类型、可读性和播放器可用性被混成一个状态 | 历史先读本地 URI，再按类型选择内部/外部播放器，并保留缺失状态。 |
| 真实 MP3 不可靠 | 仅改扩展名或依赖不确定源格式不足以保证可播 | PCM→LAME、输出内容校验与原生/仪器历史测试。 |
| Generic 直链 MP4 被误判无视频流 | yt-dlp Generic 格式携带值为 `None` 的 codec 键，旧选择器丢弃 | 通用适配器将未知 codec 显式正规化；本地 HTTP MP4 经模拟器完整下载与历史播放验证。 |
| HTML 合集成员被全部过滤 | GenericIE 将多个嵌入视频的 `webpage_url` 都设为合集页 | 成员页等于合集页时优先独立媒体 URL；两成员受控合集已在 Release 模拟器选中单项下载。 |
| 公开网页正在播放却读不到媒体 | MDN 演示位于两层开放 Shadow DOM 后的跨域 iframe，旧扫描器只看普通 DOM/同源 iframe | 遍历开放 Shadow DOM；可见跨域 iframe 由用户明确选择后再播放/读取，不从广告请求猜源。MDN 演示页已在隔离模拟器走到实际成品；封闭 Shadow DOM 与其他复杂站点未据此宣称通过。 |
| WebM 真实下载后封装失败 | VP8/Vorbis WebM 被强制作为 MP4 交给系统封装器，报 `Failed to add the track to the muxer` | 完整单文件保留 WebM 容器、扩展名和 MIME，媒体库查重同时识别 MP4/WebM；MDN 5 秒样本已在 Release 模拟器下载、历史播放和重启回读。WebM 多段拆分仍明确拒绝。 |
| Pornhub 年龄页被误作下载前置条件 | Android 嵌入 yt-dlp 请求公开页面及 CDN 时返回 410；同样本主机同版 yt-dlp 可解析，AVD 的 Java/OkHttp/标准库请求可得 200 | 只为官方单作品和对应 CDN 加兼容请求，保留网页后备；隔离 AVD 一条公开作品完成下载，不外推受限或其他作品。 |
| H.264/AAC MP4 文件存在但内置播放卡 0 秒 | 系统封装器把 ADTS 帧头连同 AAC 载荷写入 MP4 | 验证并剥除 AAC ADTS 帧头；新成品经 FFprobe 和播放器持续帧提交验证。 |

## 10. 测试、验证与部署

### 自动与隔离验证

- 2026-10-08 当前测试源码包括 55 个 JVM Kotlin 测试文件、19 个 `androidTest` 源文件和 7 个 Python 测试文件；覆盖状态机、Repository、解析、HTTP Range/续传、媒体校验、输出策略、UI 契约、会话、转码、分段、通用网站/合集及内存 SQLite 迁移。本轮 Debug/Release JVM 各 287 项、Lint 0 错误/44 警告、签名 Release 构建通过。
- `androidTest` 源文件是历史资产，但 `android/app/build.gradle.kts` 对所有 `connected*AndroidTest` 设为失败关闭，永久不得运行；尤其禁止向 OPPO 部署 Debug/仪器包。
- 1.2.82 checkpoint 已执行 Debug/Release JVM 单测和 Debug/Release lint，结果 `BUILD SUCCESSFUL`。该证据只说明代码门禁。
- 隔离模拟器通过正常 UI 路径完成抖音图文样本 2/2 与 14/14 的 MediaStore 输出；前者两张已人工确认无抖音号水印，后者 14 张逐张 `ffmpeg` 可解码。OPPO 覆盖后用户另行确认已逐张人工查看 14 张且无水印；这属于用户验收确认，不冒充本轮新增的设备命令或截图证据。
- 2026-10-08 新建的独立模拟器完成受控 MP4、MP3、HLS、DASH、动态网页与合集选中项的下载/媒体库回读；MDN 公开直链及其跨域 iframe/Shadow DOM 演示页也在签名 Release 输出实际文件，WebM 在历史内置播放器显示画面并于重启后再次打开。证据及边界见 `docs/verification/2026-10-08-website-parity-code-and-emulator.md`。

### OPPO 与正式包

- 1.2.82 正式 APK 已在 OPPO Find N5 从 1.2.81 同签名覆盖，首次安装时间及 CE/DE inode 保持，回拉 `base.apk` 与本地产物 hash 一致。
- 覆盖后用户已确认 1.2.82 完成 OPPO 抖音图文真实下载闭环，14 张样本也已逐张人工视觉确认无水印；现有项目记录未保存新的设备命令、MediaStore URI 或逐图尺寸，因此不补写这些细节。
- 正式覆盖前必须读取已装版本、`DEBUGGABLE`、证书和数据指纹；签名不同或版本不递增时停止，绝不卸载或清数据。
- 2026-10-08 新增网站范围代码未向 OPPO 覆盖；本地签名 Release 不能代替主设备实测。

## 11. 文档冲突与处理口径

下表是初版/网站范围审阅时的历史差异，不代表当前README与交接仍旧。146已完成README/源码清单及正式发布同步；当前事实看 `PROJECT_STATUS.md`。保留旧表用于追溯，不能据此重复已完成的同步。

| 文档 | 与现场冲突 | 处理 |
| --- | --- | --- |
| `README.md` | 写为开发版 1.2.67、发布版 1.2.48，构建产物示例为 1.2.8。 | 这些是面向访客的旧状态；当前版本、产物和验证看 Gradle、交接和验证记录。 |
| `context.md` | 写为 1.2.8、数据库 v6、AGP 8.5.2、旧分支。 | 与当前 Gradle 的 1.2.97、Room v10、AGP 8.6.1 不一致，只可作历史架构参考。 |
| `docs/nanzhufeng-video-downloader-development-context-for-chatgpt.md` 与 `PROJECT_HANDOFF.md` | 已更新到 1.2.82，但仍保留早期里程碑段落。 | 当前增量优先看顶部最新段和 `docs/verification/2026-08-23-android-v1.2.82-douyin-gallery-integrity.md`。 |
| 未跟踪 Claude 简报 | 基于 1.2.81 且包含已废弃方向。 | 不纳入任何当前结论或提交。 |

## 12. 已知问题、风险与后续路线

1. README已完成146同步；旧 `context.md` 仅作历史原型参考。当前项目context以本档案和状态文件为准，不再将旧副本作为当前维护入口。
2. 对每个外部平台以新的公开样本独立确认读取、下载、输出和再次打开。历史成功不可推断为平台当前可用。
3. 若修改数据库、会话、媒体输出或来源策略，先补相应迁移/契约测试，再安排隔离模拟器与经授权的正式设备验收。
4. 2026-10-09单作品网站证据属于当时范围；后续樱花、YouTube等样本边界看各日期验证与当前交接，不能用旧缺口表否定后续结果，也不能用146受控HLS成功推断所有公网网站可用。
5. 下一次生产构建配置变更需补签名凭据整组选择及部分环境配置拒绝回归；不修改本轮冻结146源码和包，完整配置/证书门禁仍须执行时核对。

## 13. 证据入口

- 当前动态事实：`PROJECT_HANDOFF.md`、`docs/verification/`、Gradle 与源码。
- 完整历史：`git log --all --reverse --format='%h|%ad|%s'`（审阅时共 105 提交）。
- Android 设计/执行史：`docs/superpowers/plans/` 与 `docs/verification/`；它们是历史证据，不取代现场代码。
- 本次正式经验增量：[146经验审计](verification/2026-10-11-app-experience-increment-audit.md)；长期经验路由：[可迁移开发经验](可迁移开发经验.md)。

## 14. 数据安全与架构边界

任务/历史持久化经Repository，输出经MediaStore/SAF事务所有者；封装失败保原始来源，不清用户缓存或覆盖旧成品。数据库变更需显式迁移与schema，正常UI验收不得数据库注入。设备/签名/发布门禁只在根 `AGENTS.md` 和专项Skill维护，本档案引用其权威规则。真实数据库、会话、密钥和用户媒体均不进入经验资产或Git。
