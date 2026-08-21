# 南枫下载 Android：ChatGPT 项目开发上下文

更新时间：2026-08-21（Asia/Shanghai）
用途：让 ChatGPT/Codex 在继续设计、排错、验证或发布前，快速理解当前 Android 项目的事实、验证边界和用户偏好。本文不替代当前源码、正式产物或真实设备证据。

## 结论先行

- 当前维护对象是 **南枫下载 Android**，不是早期 PySide/Windows 原型；Android 工程入口为 `android/`，应用 ID 为 `com.nanzhufeng.videodownloader`。
- 用户任务是把合规可访问媒体走完“读取 → 选择 → 下载/转码/合并 → MediaStore → 历史再次打开”。解析成功、文件有名字或界面能打开都不算完成。
- 当前 Git 基础提交为 `1bdcc115d1ecc1dea4aecde65c6c705f95e13b28`（`v1.2.48`）。工作树在它之上有尚未 checkpoint 的 `v1.2.57 / 10257` 媒体体验增量，不能称为已发布版本。
- `v1.2.57` 已作为正式同签名 APK 覆盖 OPPO Find N5，安装后设备 `base.apk` 哈希和本地产物一致，首次安装时间保持不变。自动门禁和签名已通过；播放中内外屏切换仍待用户人工确认，不能宣称已真机通过。
- 用户主设备数据优先级最高：永久禁止 `connected*AndroidTest`、Debug/仪器包部署、卸载、清数据和数据库注入。正式覆盖只有在用户明确授权后才可执行。

## 1. 产品定位与边界

南枫下载是面向 Android 的下载工作台，处理 YouTube、抖音、TikTok、哔哩哔哩和小红书的公开、合规可访问媒体。它提供平台读取、队列、质量选择、真实下载、音视频处理、系统媒体库发布、历史预览和永久吞吐报告。

不做会员、付费、DRM、私密内容绕过；不把账号密码、Cookie、Token、签名密钥或用户媒体写进仓库；不把网络、地区、CDN、反爬或账号限制伪装为成功。

成功定义：

1. 输入进入正确的平台发现逻辑，单条与批量入口不混淆。
2. 等待、下载、失败、取消、跳过和完成保留真实状态；失败有中文原因、行动建议、重试与删除。
3. 成品通过内容校验后发布到 MediaStore；首页、通知和历史读同一份持久事实。
4. 历史预览按本地媒体真实存在和真实类型判断；本地删除时应提示“视频已不存在”，不能误报为播放器问题。
5. 每个“支持的平台”都须独立完成当前公开样本的读取、下载、MediaStore、历史和再次打开闭环。

## 2. 当前代码、产物与验证边界

| 项目 | 当前事实 |
| --- | --- |
| Git 基础 | `1bdcc115d1ecc1dea4aecde65c6c705f95e13b28`，`release(android): prepare v1.2.48 downloader fixes` |
| 当前开发版 | `1.2.57 / 10257`，尚未 checkpoint |
| 正式 APK | `android/app/build/outputs/formal-release/南枫下载-Android-v1.2.57.apk` |
| 当前 APK SHA-256 | `4d63bb38903a27516d86aac1711e0018c2f0b67bfdc182eac3942a1644cd82fb` |
| 正式证书 SHA-256 | `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547` |
| 当前自动验证 | Debug/Release JVM 单测、Debug/Release lint、正式 APK 生成已执行；lint 为 0 error，现存 warning 需按既有基线判断 |
| 当前设备证据 | OPPO Find N5 已从 1.2.56 覆盖至 1.2.57；首次安装时间未变，回拉 `base.apk` 与本地 APK 字节哈希一致 |
| 仍待验证 | 1.2.57 播放时内外屏切换、主导航完全被覆盖、同一媒体及进度保持，必须由用户操作确认 |

当前未冻结工作树的范围：历史媒体存在性、失效记录清理、视频/图片/GIF 内容播放器、图片格式识别与保真、播放根状态所有权、折叠屏恢复以及相应测试/依赖校验。`.playwright-cli/` 是无关本机状态，不能纳入项目提交。

## 3. 技术栈、目录与所有权

| 层 | 技术 |
| --- | --- |
| UI | Kotlin、Jetpack Compose、Material 3、Navigation Compose |
| 数据 | Room、DataStore、WorkManager、前台 dataSync 服务 |
| 平台发现 | OkHttp、Chaquopy Python 3.13、yt-dlp、受控 WebView 辅助 |
| 媒体 | Media3/ExoPlayer、MediaExtractor/MediaCodec、MediaStore、Coil（GIF/ImageDecoder） |
| 音频 | LAME 4.0 JNI，PCM 后真实编码 MPEG Layer III |
| 原生与 SDK | NDK 26.1.10909125、CMake 3.22.1、`arm64-v8a`/`x86_64`；minSdk 29、compileSdk/targetSdk 35 |

```text
android/app/src/main/java/com/nanzhufeng/videodownloader/
├── core/       模型、状态策略、主题、共享 UI、中文诊断
├── data/       Room、Repository、DAO、DataStore、网络状态
├── domain/     平台发现、会话、下载、传输、输出、转码
├── feature/    home / history / settings
├── navigation/ 单 Activity Compose 壳、折叠屏导航和根播放层
└── probe/      平台读取和媒体能力探测
```

```text
分享/输入 → PlatformSourceDiscoveryEngine → yt-dlp/会话辅助
        → DownloadRepository（任务、历史、吞吐唯一事实）
        → DownloadEngine/WorkManager/DownloadTaskRunner
        → DirectMediaTransfer（Range、分片、重试、重解析）
        → 合并、分段或 PCM→LAME → 内容校验
        → MediaStoreOutputStore → 首页/通知/历史/播放器
```

核心所有者：平台识别在 `domain/discovery/`；任务、历史和失效清理由 `DownloadRepository` / `RoomDownloadRepository` 唯一管理；状态转换由 `TaskTransitionPolicy`；真实传输由 `DirectMediaTransfer`；输出由 `MediaStoreOutputStore`；中文错误由 `UserFacingErrorPresenter`；导航与视频播放会话由 `navigation/NanzhufengApp.kt`。UI 不得越过 Repository 直接写 DAO。

## 4. 已沉淀能力与历史证据

- 已建立独立正式签名、中文正式产物、签名核验和同签名无损覆盖流程；早期 Debug 到正式签名迁移与后续升级均以数据保留为硬门槛。
- 下载核心支持平台感知 Range 探测、单/多连接策略、可恢复重试、重新解析、吞吐报告、失败原位保留和 MediaStore 发布。
- 音视频支持快速转封装、真实 MP3 编码、音频/视频分段，以及完成前不虚报 100%。
- 历史页支持筛选、批量删记录、删除后重新下载但不覆盖旧成品、媒体元数据、吞吐报告与打开操作。
- 历史里程碑已覆盖抖音短链、哔哩哔哩/小红书公开样本、YouTube 视频和已结束直播、历史持久化、MediaStore 读回以及折叠屏三页布局。样本、日期、版本和验证等级以 `PROJECT_HANDOFF.md` 与 `docs/verification/` 为准；旧成功不能外推为今天每个平台仍必然可用。

## 5. 当前媒体体验增量

用户当前最关注历史页内的本地媒体体验，而非跳转系统播放器：

- **失效视频**：卡片第一行紧邻平台图标，以红色显示“视频已不存在”；点击应说明本地文件已删除。批量操作旁有“清理失效”，只删除失效历史，不删除还存在的媒体。
- **图片与动图**：普通图片、连续图片任务、GIF 和可解码的动画 WebP 由 App 内查看器打开；连续图片可左右滑动，格式/MIME 必须来自真实内容而不是任务标签猜测。
- **视频**：默认 App 内播放；一次返回/边缘返回即可退出，不依赖外部播放器作为主路径。
- **折叠屏**：`MainActivity` 接管配置变化；播放会话（任务 ID、URI、进度、播放态）由根 `NanzhufengApp` 唯一拥有，播放器覆盖层绘制在主导航之后。它针对“折叠退出”“主导航露出”“回到上一条视频”三个被用户否定的问题。

该根层方案已编译并覆盖到 OPPO，但还没有用户对 1.2.57 的最终折叠体验确认。继续开发时必须先保留这个待验证状态。

## 6. 构建、OPPO 与验收

```bash
cd /Users/nanzhufeng/Documents/工具开发/NanzhufengVideoDownloader-Android/android
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
NANZHUFENG_BUILD_PYTHON='/opt/homebrew/bin/python3.13' \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:testReleaseUnitTest \
  :app:lintRelease :app:stageFormalReleaseArtifacts --max-workers=1
```

构建/单测/lint 仅证明静态门禁，不能替代真实下载、模拟器或 OPPO 验收。任何 `connected*AndroidTest` 都禁止执行。

OPPO 正式覆盖顺序：只读确认唯一设备、当前包版本、`firstInstallTime` 与已装证书 → 核验新正式包版本/签名 → 用户明确授权后 `adb push` 到精确临时路径并 `pm install -r --user 0` → 回读版本和首次安装时间、拉回 `base.apk` 比对 SHA-256 → 清理精确临时文件。签名不一致、需要确认或数据异常时立即停止，绝不卸载或清数据。

## 7. 给 ChatGPT 的开发判断

- 先把反馈转成“真实对象、唯一所有者、失败状态、用户行动、验证门槛”，不能只修截图。
- 媒体存在性、真实类型、可播放性和历史状态是四个不同概念；不要把 `ActivityNotFound` 当成文件不存在，也不要把 URI 存在当作文件有效。
- 折叠、旋转或导航会重建局部分支时，播放器和播放会话必须由不随局部销毁的根层持有；不要用更大的 `rememberSaveable` 修补错误的窗口/所有权边界。新会话用稳定任务和媒体标识隔离，防止旧回调覆盖新媒体。
- 写入图片或动图时先识别真实格式并保留原始 MIME/扩展名；展示能力由真实内容选择，而非“分辨率看起来像视频”。
- 用户要求“一个增量，一个验收”：分开报告代码、自动门禁、模拟器、OPPO、外部服务和待验证项。未执行的层级不能暗示完成。
- 所有新用户可见功能默认应在“设置 → 功能审阅”列出；除非明确授权，不新增常驻首页入口。

## 8. 风险、下一步与事实源

1. 先收集用户对 1.2.57 内外屏播放的反馈；若仍失败，优先记录 taskId/URI、配置变化和播放器创建/释放这一条链路，再决定修复点。
2. 第三方平台受 CDN、地区、账号和反爬影响；每次平台变更都用新公开样本独立完成全链路，不复用旧成功。
3. 批量作者/频道/播放列表、断网恢复、通知和权限均需独立真实验收。
4. 当前工作树尚未 checkpoint。用户要求 checkpoint 时，只显式暂存本轮项目文件，排除 `.playwright-cli/` 与构建产物，检查 staged diff/秘密后创建本地提交；未授权不得推送或创建 Release。

权威来源优先级：当前源码、`android/app/build.gradle.kts`、正式 APK 和设备回读 > `PROJECT_HANDOFF.md` / `docs/verification/` > `context.md` > 本文 > 历史记忆。`README.md` 面向仓库访客，版本文字可能落后于未提交开发版，使用前必须核对当前 Gradle 配置。
