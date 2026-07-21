# 南枫下载 Android 项目 Context

更新时间：2026-07-21（Asia/Shanghai）

> 本文件是当前 Android 项目的快速事实入口，供开发者和后续 Codex 任务使用。当前代码与最新真实验证证据高于本文；详细里程碑与真机记录见 `PROJECT_HANDOFF.md`。

## 1. 当前快照

| 项目 | 当前事实 |
|---|---|
| 产品名 | 南枫下载 |
| 平台 | Android；当前仓库的正式维护对象不是 Windows/macOS 版 |
| 仓库根目录 | `/Users/nanzhufeng/Documents/工具开发/NanzhufengVideoDownloader-Android` |
| Android 工程 | `android/` |
| 当前分支 | `codex/android-ui-download-core-checkpoint-20260718` |
| 当前 HEAD | `20a85c8c1935`（当前成果尚未建立新检查点提交） |
| Git 状态 | 文档固化时为 5 项已跟踪修改 + 1 个未跟踪 `context.md`；本轮未删除、覆盖、stash、reset 或提交 |
| applicationId | `com.nanzhufeng.videodownloader` |
| 当前版本 | `0.1.0-probe` / `versionCode 1` |
| 构建产物 | `android/app/build/outputs/apk/debug/南枫下载.apk` |
| 发布状态 | Debug 真机验收版；尚未配置正式版本号、Release 签名和 AAB 发布链路 |
| 主要真机 | OPPO Find N5 / PKH120；外屏 1140×2616，内屏 2248×2480 |

当前 Git `origin` 仍指向本地交接 bundle，不是可识别的 GitHub URL。若后续要继续发布或推送，必须先核对正确的 Android 专用远端，不要直接覆盖现有远端配置。

## 2. 产品核心与边界

第一用户任务：用户粘贴或分享 YouTube、抖音、TikTok 的公开单视频/直播回放链接，App 完成读取、去重、入队、下载、必要的音视频合并或 MP3 转码、系统媒体库发布，并在下载列表与历史中提供真实状态、中文错误和吞吐报告。

核心成功标准：

1. 公开单视频尽量无需登录即可读取和下载；受限、批量或平台要求时再使用 WebView 会话/Cookie。
2. 等待、下载、失败、取消、重复跳过和完成状态不能互相伪装；未完成任务必须留在下载列表中。
3. 视频写入系统 `Movies`，MP3 写入系统 `Music`，历史记录和吞吐报告可在 App 重启后继续读取。
4. YouTube、抖音、TikTok 必须以真实公开内容走完“读取 → 入队 → 下载 → MediaStore 成品 → 历史”链路；只打开页面或解析成功不算完成。
5. 不绕过会员、付费、DRM 或私密内容；不在项目中保存账号密码、Cookie、Token 或签名密钥。

## 3. 技术栈

### Android 与构建

| 层级 | 技术与版本 |
|---|---|
| 构建 | Gradle 8.7、Android Gradle Plugin 8.5.2、Kotlin 2.0.20、KSP 2.0.20-1.0.25 |
| Android SDK | `minSdk 24`、`compileSdk 35`、`targetSdk 35` |
| JVM | Java 17 / Kotlin JVM target 17 |
| UI | Jetpack Compose、Material 3、Compose BOM 2024.06.00、Navigation Compose 2.8.0 |
| 异步与生命周期 | Kotlin Coroutines 1.8.1、Lifecycle 2.8.4 |
| 后台任务 | WorkManager 2.9.1 + 前台 dataSync 服务 |
| 数据库 | Room 2.6.1，数据库版本 4，显式迁移 1→2→3→4 |
| 设置 | DataStore Preferences 1.1.1 |
| 网络 | OkHttp 4.12.0；平台感知的 Range 探测、单/多连接传输、重试和重新解析 |
| 图片 | Coil Compose 2.7.0 |
| 媒体 | Android MediaExtractor/MediaCodec、MediaStore、Media3 1.8.1 |
| 测试 | JUnit 4、MockWebServer、AndroidX Test/Espresso、Compose UI Test、Room Testing |

### Python 与原生媒体链路

| 层级 | 技术与版本 |
|---|---|
| Python 嵌入 | Chaquopy 17.0.0，目标 Python 3.13 |
| 平台解析 | `yt-dlp==2026.6.9`，由 Kotlin 领域入口调用嵌入式 Python |
| 原生构建 | Android NDK 26.1.10909125、CMake 3.22.1 |
| ABI | `arm64-v8a`、`x86_64` |
| MP3 | LAME 4.0 动态库 + JNI；Android 解码为 PCM 后再编码真实 MPEG Layer III |

## 4. 架构与数据流

项目采用单 Activity + Compose、手写 `AppContainer` 依赖装配、领域用例、Repository、Room/DataStore 和 WorkManager 的分层结构。

```text
系统分享 / 首页输入 / 登录会话
        ↓
PlatformSourceDiscoveryEngine
        ↓
ChaquopyProbeDiscoveryGateway + yt-dlp + WebViewSessionProvider
        ↓
RoomDownloadRepository（媒体、任务、历史、吞吐报告）
        ↓
DefaultDownloadEngine → WorkManagerDownloadScheduler
        ↓
ForegroundDownloadWorker → DownloadTaskRunner
        ↓
YtDlpTaskMediaResolver → DirectMediaTransfer
        ↓
Range 探测 / 分片下载 / 重试与重新解析
        ↓
视频音频合并或 PCM → LAME MP3
        ↓
MediaStoreOutputStore → Movies / Music
        ↓
首页、通知、历史与吞吐报告读取同一持久状态
```

关键概念所有者：

| 概念 | 当前唯一所有者/入口 |
|---|---|
| App 依赖装配 | `AppContainer.kt` |
| 平台识别与作品发现 | `PlatformSourceDiscoveryEngine.kt` |
| 会话与 Cookie | `SessionProvider.kt` / `WebViewSessionProvider.kt` |
| 下载任务与历史真值 | `DownloadRepository.kt` / `RoomDownloadRepository.kt` |
| 状态迁移 | `TaskTransitionPolicy.kt` |
| 调度 | `DownloadEngine.kt` / `WorkManagerDownloadScheduler` |
| 单次任务执行 | `DownloadTaskRunner.kt` |
| 媒体解析 | `YtDlpTaskMediaResolver.kt` |
| Range/分片与真实传输 | `DirectMediaTransfer.kt`、`StreamDownloadCoordinator.kt`、`PlatformTransferPolicy.kt` |
| 性能报告 | `DownloadPerformanceReporter.kt` + Room `download_throughput_reports` |
| 文件输出 | `MediaStoreOutputStore.kt` / `OutputFilePolicy.kt` |
| MP3 转码 | `domain/download/audio/` + `cpp/nanzhufeng_mp3_jni.cpp` |
| 中文用户错误 | `UserFacingErrorPresenter.kt` |
| 三页导航与折叠屏壳 | `navigation/NanzhufengApp.kt` |
| 首页/下载列表 | `feature/home/` |
| 完成历史与媒体打开 | `feature/history/` |
| 登录、质量和任务设置 | `feature/settings/` |

## 5. 目录结构

```text
NanzhufengVideoDownloader-Android/
├── context.md                    # 当前项目快速事实入口
├── PROJECT_HANDOFF.md            # 详细里程碑、验证证据、阻塞和待办
├── design-qa.md                  # 最终折叠屏 UI 对照结论
├── android/                      # 当前权威 Android 工程
│   ├── app/
│   │   ├── build.gradle.kts      # App、依赖、Chaquopy、NDK、APK 命名
│   │   ├── schemas/              # Room 导出 schema
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/nanzhufeng/videodownloader/
│   │       │   │   ├── core/     # 模型、状态策略、主题、共享 UI、中文诊断
│   │       │   │   ├── data/     # Room、Repository、网络状态、DataStore
│   │       │   │   ├── domain/   # 发现、会话、下载、转码、输出
│   │       │   │   ├── feature/  # home / history / settings
│   │       │   │   ├── navigation/
│   │       │   │   └── probe/    # 平台解析和媒体能力探测
│   │       │   ├── python/       # Chaquopy Python 包
│   │       │   ├── cpp/          # JNI、CMake、LAME 4.0 源码
│   │       │   ├── res/          # Compose 辅助资源、图标、主题、应用名
│   │       │   └── assets/       # 第三方许可和随包资产
│   │       ├── test/              # JVM 单元与契约测试
│   │       └── androidTest/       # Room、媒体、转码、UI 和真机能力测试
│   ├── build.gradle.kts           # 顶层插件版本
│   ├── settings.gradle.kts
│   └── gradlew
├── docs/
│   ├── superpowers/specs/         # 已确认设计规格
│   ├── superpowers/plans/         # 历史实施计划
│   ├── verification/              # 阶段验证记录
│   └── assets/android/            # 参考与验收图
├── .artifacts/                    # 本机 UI/验证证据，通常不入 Git
└── app/、start.py、run.ps1 等     # 旧桌面原型遗留；不是当前 Android 构建入口
```

根目录 `README.md` 和 `docs/nanzhufeng-video-downloader-development-context-for-chatgpt.md` 主要描述旧桌面原型，目前不能代替本文件或 Android 交接文档。不要根据它们修改 Android 产品边界。

## 6. 构建、测试与安装

### Mac 构建环境

- Android Studio JBR/可用 JDK 17。
- Android SDK 35、NDK 26.1.10909125、CMake 3.22.1。
- Python 3.13；可通过 Gradle 属性 `nanzhufeng.buildPython` 或环境变量 `NANZHUFENG_BUILD_PYTHON` 指定。
- 当前 Clash/TUN 网络下首次依赖解析建议使用 `--max-workers=1`，避免并发 TLS 抖动。

### 标准门禁

```bash
cd /Users/nanzhufeng/Documents/工具开发/NanzhufengVideoDownloader-Android/android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1
```

自动 UI 测试必须明确绑定项目专用模拟器，不能把测试 APK 推到 OPPO 真机。Android UI 开发默认保持项目模拟器可见。

### OPPO 安装

OPPO 真机固定使用“推送 APK → `pm install -r --user 0`”的同签名覆盖路径，保留 App 数据；不要使用会触发 ColorOS 图形确认页的 `adb install`。

```bash
adb -s <OPPO序列号> push \
  android/app/build/outputs/apk/debug/南枫下载.apk \
  /data/local/tmp/nanfeng-download.apk

adb -s <OPPO序列号> shell \
  pm install -r --user 0 /data/local/tmp/nanfeng-download.apk
```

若签名不一致、设备离线或系统仍要求人工确认，立即停止并报告；不得卸载、清数据或强行绕过。

## 7. 当前验证等级

本次最终回归以当前未提交成果为对象，所有自动测试仅运行在专用模拟器 `emulator-5580`：

- `:app:testDebugUnitTest`：127 项，0 失败、0 错误、0 跳过。
- `:app:lintDebug`：通过，无阻断问题。
- `:app:assembleDebug`：通过；强制重跑时 69 个可执行任务全部实际执行，`BUILD SUCCESSFUL in 1m 4s`。
- arm64-v8a 与 x86_64 均产生 `libnanzhufeng_mp3.so`。
- 完整模拟器仪器测试：58 项，0 失败；测试 APK 仅通过指定序列号安装到 `emulator-5580`，未接触 OPPO。
- 设置页紧凑度测试原先用 28% 硬阈值限制两张等高卡片；真实测量均为 `268.6dp / 947.0dp = 28.36%`。测试统一校正为 29% 容差并永久输出实际比例，生产 UI 未改动。
- 当前 APK：68,046,080 B，SHA-256 `01b01b2386934b82a12b87e3c4f3466a786c6426b20f0ebf48a4d411d43659df`。该哈希对应最后一次完整门禁后的唯一最终产物。
- OPPO Find N5 通过固定的“推送 APK → `pm install -r --user 0`”路径同签名覆盖成功，未卸载、未清数据；从手机拉回的 `base.apk` 与本地 APK 哈希完全一致，冷启动成功。
- OPPO 真实搜狗键盘开启时，“智能读取 / 清空”整排完整位于键盘上方；键盘关闭后操作区恢复原坐标，用户已确认该效果正确。
- 本轮没有重跑 YouTube、抖音、TikTok 的真实网络下载；三平台成品、MediaStore 读回和吞吐报告结论仍引用 `PROJECT_HANDOFF.md` 已记录的最新里程碑证据。

已确认的最新里程碑证据来自 `PROJECT_HANDOFF.md`：

- JVM 契约、lint 和 Debug APK 构建已通过。
- 项目专用模拟器已覆盖三页 UI、Room、媒体、转码和主要下载链路。
- OPPO Find N5 已完成外屏/内屏切换、YouTube/抖音/TikTok 真实成品、MediaStore 读回、完成历史和永久吞吐报告验证。
- YouTube 大文件多连接实测平均达到约 12 MB/s；抖音/TikTok 小文件在当前样本中走单连接约 2.8–2.9 MB/s。速度受 CDN、网络和账号条件影响，不能承诺固定结果。
- YouTube `/live/` 已结束直播回放已完成真实下载；失败尝试保留在下载列表并提供中文错误、重试和删除。

历史测试数量和 APK 哈希不得脱离对应工作区快照或提交重复使用。

## 8. 已知风险与下一步

1. 当前仍是 `0.1.0-probe` Debug 验收版；正式版本号、发布签名、Release APK/AAB 和升级策略尚未完成。
2. 抖音等平台的受限内容可能要求 fresh cookies；会话存在不等于登录真实有效，必须用受保护动作验证。
3. 作者/频道/播放列表的大批量分页、过滤和取消选择仍需要更长的公开内容回归。
4. 断网恢复、用户暂停后不自动恢复和完成通知虽有自动化覆盖，但最新里程碑没有逐项重新做 OPPO 人工触发。
5. Git 远端目前是本地 bundle；对外发布前需要核对 Android 专用 GitHub 仓库和远端真实性。
6. 根目录混有旧桌面原型资产；当前任务不得顺手删除或迁移。若要拆分仓库，应另开任务并先确认历史与发布边界。
7. 当前 AGP 8.5.2 官方测试范围只到 compileSdk 34，但项目使用 compileSdk 35；本轮构建通过，仍应在 Release 收口时升级或重新确认兼容矩阵。
8. CMake 配置阶段提示 Android SDK XML v4 与当前原生工具只理解到 v3；本轮双 ABI 构建成功，但 SDK command-line tools 与 Android Studio/NDK 版本仍需在发布前对齐。
9. 强制重跑与后续增量打包间，Debug APK 的 Chaquopy `requirements-common.imy`、`app.imy` 和 `build.json` 会重新打包，导致字节大小和哈希变化；APK 内容差异已收窄到这 3 个 Chaquopy 资产，但 Release 收口前仍需建立可重复打包验证。

## 9. 后续任务读取顺序

1. `context.md`：快速了解当前 Android 事实、技术栈和目录。
2. `PROJECT_HANDOFF.md`：读取详细真机证据、完成项和未完成门槛。
3. 当前代码与测试：确认文档是否已漂移。
4. 对应 `docs/superpowers/specs/`：仅在相关功能或 UI 任务中读取已确认规格。
5. `design-qa.md`：仅在视觉回归、折叠屏或截图验收时读取。

禁止把旧桌面 README、早期设计拼图、历史计划或单次构建输出当成当前 Android 项目的唯一真相源。

### UI 参考的强制例外

当其他 App 说“参考南枫下载”时，不得直接读取 `docs/assets/android/find-n5-home-*.png`、`.artifacts/**/reference/**`、`.artifacts/**/reference-normalized/**` 或 `.artifacts/**/final-pairs/**` 作为最终设计。这些均为早期稿、参考归一化素材或拼合对比板。

最终 UI 事实按以下顺序获取：用户当前确认；Codex 任务 `019f6c95-868f-7aa2-a5cb-fd7e32640914` 的确认回合 `019f7509-a178-7af0-8d21-cdf2779dd69e`；OPPO 当前安装版；本文件所指向的 `android/` 当前代码。需要精确视觉时重新逐页采集当前安装版的外屏/内屏独立截图，并记录包版本、提交和日期，不凭文件名猜测。
