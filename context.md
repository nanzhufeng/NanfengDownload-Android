# 南枫下载 Android 项目 Context

更新时间：2026-07-26（Asia/Shanghai）

> 本文件是当前 Android 项目的快速事实入口，供开发者和后续 Codex 任务使用。当前代码与最新真实验证证据高于本文；详细里程碑与真机记录见 `PROJECT_HANDOFF.md`。

## 1. 项目身份与当前快照

| 项目 | 当前事实 |
|---|---|
| 产品名 | 南枫下载 |
| 平台 | Android；当前仓库的正式维护对象不是 Windows/macOS 版 |
| 仓库根目录 | `/Users/nanzhufeng/Documents/工具开发/NanzhufengVideoDownloader-Android` |
| Android 工程 | `android/` |
| 当前分支 | `codex/android-ui-download-core-checkpoint-20260718` |
| 当前代码 checkpoint | 当前分支包含 `v1.1.0` 哔哩哔哩与小红书扩展及 OPPO 验收证据；GitHub `v1.0.0` 仍是最新公开发布 |
| Git 状态 | `v1.1.0` 双平台扩展按本文件与 verification 记录建立本地 checkpoint，不代表已推送 GitHub |
| applicationId | `com.nanzhufeng.videodownloader` |
| 当前版本 | `1.1.0` / `versionCode 10100` |
| 构建产物 | `android/app/build/outputs/formal-release/南枫下载-Android-v1.1.0.apk` 与同名 `.aab` |
| 发布状态 | `v1.1.0` 已完成本地正式构建与 OPPO 验收；GitHub 最新公开正式版仍为 `v1.0.0` |
| 主要真机 | OPPO Find N5 / PKH120；外屏 1140×2616，内屏 2248×2480 |

当前 Git `origin` 已核对为私有 Android 专用仓库 `https://github.com/nanzhufeng/NanfengDownload-Android.git`，默认分支为 `main`。原本地交接 bundle 以 `handoff-bundle` 远端名保留，不作为日常推送目标。

## 2. 产品核心、边界与数据安全

第一用户任务：用户粘贴或分享 YouTube、抖音、TikTok、哔哩哔哩和小红书的公开单视频/直播回放链接，App 完成读取、去重、入队、下载、必要的音视频合并或 MP3 转码、系统媒体库发布，并在下载列表与历史中提供真实状态、中文错误和吞吐报告。

核心成功标准：

1. 公开单视频尽量无需登录即可读取和下载；受限、批量或平台要求时再使用 WebView 会话/Cookie。
2. 等待、下载、失败、取消、重复跳过和完成状态不能互相伪装；未完成任务必须留在下载列表中。
3. 视频写入系统 `Movies`，MP3 写入系统 `Music`，历史记录和吞吐报告可在 App 重启后继续读取。
4. 每个声明支持的平台必须以真实公开内容走完“读取 → 入队 → 下载 → MediaStore 成品 → 历史”链路；只打开页面或解析成功不算完成。
5. 不绕过会员、付费、DRM 或私密内容；不在项目中保存账号密码、Cookie、Token 或签名密钥。

## 3. 技术栈

### Android 与构建

| 层级 | 技术与版本 |
|---|---|
| 构建 | Gradle 8.7、Android Gradle Plugin 8.5.2、Kotlin 2.0.20、KSP 2.0.20-1.0.25 |
| Android SDK | `minSdk 29`、`compileSdk 35`、`targetSdk 35` |
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

根目录 `README.md` 已更新为当前 Android 正式版入口；`docs/nanzhufeng-video-downloader-development-context-for-chatgpt.md` 仍是旧桌面原型资料，不能代替本文件或 Android 交接文档。

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
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease \
  :app:stageFormalReleaseArtifacts --max-workers=1
```

自动 UI 测试必须明确绑定项目专用模拟器，不能把测试 APK 推到 OPPO 真机。Android UI 开发默认保持项目模拟器可见。

### OPPO 安装

OPPO 真机固定使用“推送 APK → `pm install -r --user 0`”的同签名覆盖路径，保留 App 数据；不要使用会触发 ColorOS 图形确认页的 `adb install`。

```bash
adb -s <OPPO序列号> push \
  android/app/build/outputs/formal-release/南枫下载-Android-v1.1.0.apk \
  /data/local/tmp/nanfeng-download.apk

adb -s <OPPO序列号> shell \
  pm install -r --user 0 /data/local/tmp/nanfeng-download.apk
```

若签名不一致、设备离线或系统仍要求人工确认，立即停止并报告；不得卸载、清数据或强行绕过。

## 7. 当前验证等级

当前 `v1.1.0` 哔哩哔哩与小红书扩展以最终 Release APK/AAB 为对象：

- Python 解析测试：17 项，0 失败。
- `:app:testDebugUnitTest`：140 项，0 失败、0 错误、0 跳过。
- `:app:testReleaseUnitTest`：140 项，0 失败、0 错误、0 跳过。
- `:app:lintRelease`：通过，无阻断问题。
- 专用模拟器 `emulator-5554` 仪器测试：58 项，0 失败；外部 TikTok 条件按既有规则跳过。
- arm64-v8a 与 x86_64 均产生 `libnanzhufeng_mp3.so`。
- Release APK：91,327,503 B，SHA-256 `8f03cb499aa0db94394bcd8e23070cab163a80520343a1dbfa8062331b37f47a`。
- Release AAB：38,262,440 B，SHA-256 `fafcd2f2f50cabed3241fecc5256e98434269c0b3c583884087fb6b7158aaea7`。
- APK 为 `1.1.0 / 10100`、`minSdk 29`、`targetSdk 35`、`debuggable=false`，使用既有正式证书；证书 SHA-256 为 `C4:FB:47:E2:76:B5:A9:38:1E:53:62:E8:D1:76:CC:B9:E1:71:A0:34:F5:13:C9:D8:11:D4:7A:53:64:0F:45:47`。
- 正式 APK 在专用模拟器冷启动成功；从模拟器拉回的 `base.apk` 与本地 APK 哈希一致。
- 哔哩哔哩与小红书均在模拟器及 OPPO 完成真实网络成品闭环；OPPO 上的系统媒体库文件、历史、吞吐报告、重复去重和重启持久性均已验证。

已确认的最新里程碑证据来自 `PROJECT_HANDOFF.md`：

- JVM 契约、lint 和 Debug APK 构建已通过。
- 项目专用模拟器已覆盖三页 UI、Room、媒体、转码和主要下载链路。
- OPPO Find N5 已完成外屏/内屏切换、YouTube/抖音/TikTok 真实成品、MediaStore 读回、完成历史和永久吞吐报告验证。
- YouTube 大文件多连接实测平均达到约 12 MB/s；抖音/TikTok 小文件在当前样本中走单连接约 2.8–2.9 MB/s。速度受 CDN、网络和账号条件影响，不能承诺固定结果。
- YouTube `/live/` 已结束直播回放已完成真实下载；失败尝试保留在下载列表并提供中文错误、重试和删除。

历史测试数量和 APK 哈希不得脱离对应工作区快照或提交重复使用。

## 8. 已知风险与下一步

1. 正式签名密钥只保存在仓库外的本机安全目录，密码在 macOS 钥匙串；仍应建立用户控制的异地加密备份，密钥丢失将导致后续正式版无法覆盖升级。
2. OPPO 已在 2026-07-21 通过 Android v3 证书谱系从旧 Debug 版无损升级到正式签名 `v1.0.0`；2026-07-26 又以同一正式证书无损覆盖到 `v1.1.0`。首次安装时间、旧历史和输入草稿均保留，后续仍不得卸载或清数据。
3. 抖音等平台的受限内容可能要求 fresh cookies；会话存在不等于登录真实有效，必须用受保护动作验证。
4. 哔哩哔哩 UP 主页批量接口当前返回 412，`v1.1.0` 只支持单视频；其他作者/频道/播放列表的大批量分页、过滤和取消选择仍需要更长的公开内容回归。
5. 当前 AGP 8.5.2 官方测试范围只到 compileSdk 34，但项目使用 compileSdk 35；本轮 Release 构建通过，后续仍应升级 AGP 或重新确认兼容矩阵。
6. CMake 配置阶段提示 Android SDK XML v4 与当前原生工具只理解到 v3；本轮双 ABI Release 构建成功，但 SDK command-line tools 与 Android Studio/NDK 版本仍需对齐。
7. 正式 APK/AAB 本轮只冻结一次最终哈希；任何重新打包都会使当前校验值失效，必须重新验证并更新 Release。

## 9. 后续任务读取顺序

1. `context.md`：快速了解当前 Android 事实、技术栈和目录。
2. `PROJECT_HANDOFF.md`：读取详细真机证据、完成项和未完成门槛。
3. 当前代码与测试：确认文档是否已漂移。
4. 对应 `docs/superpowers/specs/`：仅在相关功能或 UI 任务中读取已确认规格。
5. `design-qa.md`：仅在视觉回归、折叠屏或截图验收时读取。
6. `docs/verification/2026-07-21-android-v1.0.0-release.md`：正式签名、Release APK/AAB、哈希和模拟器安装证据。
7. `docs/verification/2026-07-26-android-v1.1.0-bilibili-xiaohongshu.md`：两个新增平台的边界、模拟器与 OPPO 真实网络闭环、正式产物及持久化证据。

禁止把旧桌面 README、早期设计拼图、历史计划或单次构建输出当成当前 Android 项目的唯一真相源。

### UI 参考的强制例外

当其他 App 说“参考南枫下载”时，不得直接读取 `docs/assets/android/find-n5-home-*.png`、`.artifacts/**/reference/**`、`.artifacts/**/reference-normalized/**` 或 `.artifacts/**/final-pairs/**` 作为最终设计。这些均为早期稿、参考归一化素材或拼合对比板。

最终 UI 事实按以下顺序获取：用户当前确认；Codex 任务 `019f6c95-868f-7aa2-a5cb-fd7e32640914` 的确认回合 `019f7509-a178-7af0-8d21-cdf2779dd69e`；OPPO 当前安装版；本文件所指向的 `android/` 当前代码。需要精确视觉时重新逐页采集当前安装版的外屏/内屏独立截图，并记录包版本、提交和日期，不凭文件名猜测。
