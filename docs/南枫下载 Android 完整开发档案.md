# 南枫下载 Android 完整开发档案

> 现行合同从[合同总入口](contracts/README.md)读取；本页保留自身规则/架构/经验职责，不重复定义参数。

初版：2026-08-23；全项目复盘：2026-10-11（Asia/Shanghai）。本档案记录有代码、Git或验证文档依据的事实；当前状态由[PROJECT_STATUS](PROJECT_STATUS.md)维护，具体接续由[交接](../PROJECT_HANDOFF.md)维护。

目录：[目标](#1-项目身份目标与边界) · [技术栈](#3-技术栈与配置) · [目录](#4-目录结构) · [架构与数据流](#5-架构数据流持久化与数据安全) · [模块](#6-核心模块与所有权) · [决策](#7-关键决策及原因) · [过程](#8-开发过程与git脉络) · [踩坑](#9-踩坑根因与修复) · [验证](#10-测试与验证证据) · [部署](#11-构建部署与交付) · [冲突](#12-文档与源码冲突处理) · [风险](#13-已知问题未确认事项与后续路线)

## 1. 项目身份、目标与边界

项目产品为南枫下载 Android，工程入口 `android/`，应用ID `com.nanzhufeng.videodownloader`。用户任务是把明确可访问的来源经过“读取→选择/去重→下载或处理→发布→历史再次打开”得到完整媒体，而不是单独得到链接、入队提示或网络字节。来源、传输、发布、历史与播放分别验收。

当前代码包含YouTube、抖音、TikTok、哔哩哔哩、小红书、Pornhub及通用HTTP(S)网页/媒体/合集路由。单作品与批量能力并不一致：B站UP主页明确拒绝，通用动态播放器需用户确认；不将某平台单例成功扩写为全站支持。依据：[UrlClassifier](../android/app/src/main/java/com/nanzhufeng/videodownloader/probe/UrlClassifier.kt)、[发现引擎](../android/app/src/main/java/com/nanzhufeng/videodownloader/domain/discovery/PlatformSourceDiscoveryEngine.kt)。

产品不以付费、会员、DRM、私密或地区访问限制绕过为目标；受限内容与不支持格式失败保留原因。根目录 `app/`、`start.py`、桌面依赖和PyInstaller脚本是初始PySide原型，未作为本轮Android构建或业务验证对象。依据：Git `4077045`、[桌面入口](../start.py)、[桌面依赖](../requirements.txt)。

## 2. 复盘范围与事实基线

- 复盘开始HEAD：`156f6b2074e7a75f5aab9a05a53ec3bcc436b6f9`，分支main；实际验证生产代码为 `d0602d13c87411f5225889c04ef9a6f403a2a362`，后续提交为文档/核对工具。
- 开始清单：778个Git受控路径，726个现存文本、51个二进制、1个先前本地报告删除；114条可达Git提交。全量文件分类、文本索引/风险扫描、Python语法与结构化配置解析及核心链定向核对见[本轮复盘记录](verification/2026-10-11-full-project-retrospective.md)。这不等于逐行算法证明、全格式验收或重新跑所有历史设备路径。
- 当前源码1.2.146/10346、Room11；479个生产文件与指定生产提交、冻结APK清单相同。元数据见[artifact](releases/v1.2.146/artifact.json)与[源码清单](releases/v1.2.146/source-manifest.json)。本轮不改业务代码，不重新打包、安装、推送或发布。
- 动态设备、网络、版本和提交数量只在日期快照有效；后续必须读取当前代码与适用证据，不把本档案当现装状态证明。

## 3. 技术栈与配置

| 层 | 源码现场事实 | 依据 |
| --- | --- | --- |
| SDK/语言 | minSdk29、compile/target35；Java/Kotlin目标17 | [app Gradle](../android/app/build.gradle.kts) |
| 构建 | Gradle8.7、AGP8.6.1、Kotlin2.0.20、KSP2.0.20-1.0.25 | [根Gradle](../android/build.gradle.kts)、[wrapper](../android/gradle/wrapper/gradle-wrapper.properties) |
| UI | Compose BOM2024.06.00、Material3、Navigation2.8.0、Coil2.7.0图片/GIF/视频 | app Gradle与core/ui、navigation |
| 异步/后台 | Coroutines1.8.1、Lifecycle2.8.4、WorkManager2.10.5，dataSync前台Worker | app Gradle、[Manifest](../android/app/src/main/AndroidManifest.xml) |
| 数据 | Room2.6.1、DataStore Preferences1.1.1；播放位置用独立SharedPreferences | app Gradle、data/database、data/settings、data/playback |
| 网络/发现 | OkHttp4.12.0、Chaquopy17.0.0/Python3.13、yt-dlp2026.8.19与明确SHA256 | [嵌入Python依赖](../android/app/src/main/python/requirements.txt) |
| 媒体 | Media3 1.7.1、MediaExtractor/MediaCodec、MediaStore/SAF | domain/download、feature/history |
| 原生音频 | NDK26.1.10909125、CMake3.22.1、随仓库LAME4.0与JNI | [CMake](../android/app/src/main/cpp/CMakeLists.txt) |
| ABI | arm64-v8a、x86_64 | app Gradle |

Manifest启用Internet、网络状态、通知、dataSync前台服务与悬浮窗权限；`allowBackup=false`，备份/迁移XML排除私有数据域。为通用HTTP来源启用应用级明文流量，此范围是项目选择，不是跨项目默认建议。主入口可接收text/plain分享，登录/捕获Activity不导出。依据：Manifest、[备份规则](../android/app/src/main/res/xml/backup_rules.xml)、[提取规则](../android/app/src/main/res/xml/data_extraction_rules.xml)。

## 4. 目录结构

```text
android/
  app/build.gradle.kts、proguard-rules.pro   版本/依赖/签名/R8/正式产物门禁
  gradle/、gradlew                         wrapper与依赖校验元数据
  app/schemas/                            Room 1至11导出快照
  app/src/main/java/com/nanzhufeng/videodownloader/
    core/                                 模型、状态转换、错误与共享UI
    data/                                 Room/DAO/Repository、设置、网络、播放位置
    domain/discovery、session              来源发现、会话与凭据作用域
    domain/download/                      调度、传输、发布、性能、后台岛
    domain/download/audio、video          解码/MP3、无损合并/封装/分段
    domain/playback                       播放记忆与手势策略
    feature/home、history、settings        三页交互、媒体预览、登录与权限入口
    navigation、probe                     根导航/播放会话、平台适配/HTTP/协议下载
  app/src/main/python/nanzhufeng_probe/    解析、合集、代理、HLS/DASH传输与TS时间轴
  app/src/main/cpp/                        JNI、Android配置与第三方LAME
  app/src/main/assets、res/                脚本/字体/许可、图标/样式/资源
  app/src/test/                           JVM、Python、JS、合成媒体与本地服务夹具
  app/src/androidTest/                    历史仪器源码/夹具，永久不执行
app/、tests/、start.py                     旧桌面原型与其测试
.agents/skills/                           开发/排错/测试/审查/文档同步五类流程
tools/                                   冻结产物核对与单测重跑配置
docs/                                    合同、验证、历史计划/交接、截图与发布元数据
```

历史根目录XML/PNG是旧操作/预览资产，不是当前Android打包源或当前UI通过证据；不要因全盘整理擅自删除。实际清单以Git为准。

## 5. 架构、数据流、持久化与数据安全

[AppContainer](../android/app/src/main/java/com/nanzhufeng/videodownloader/AppContainer.kt)是组合根：显式装配数据库、Repository、设置、会话、发现、任务运行器、输出存储、调度和网络观察。Application持有容器，MainActivity承载Compose根；UI不直接写DAO。

```text
粘贴 / 分享
 → UrlClassifier → PlatformSourceDiscoveryEngine → 平台轻量元数据/受限捕获/嵌入解析
 → 用户选择 → DownloadRepository.enqueue → Room队列
 → DownloadEngine → WorkManagerDownloadScheduler → ForegroundDownloadWorker
 → DownloadTaskRunner → YtDlpTaskMediaResolver → DirectMediaTransfer
 → HTTP直链或ProtocolFileDownloader → 必要合并/转码/分段/结构校验
 → MediaStoreOutputStore（MediaStore或SAF）→ DownloadHistory
 → 首页 / 通知 / 灵动岛 / 历史预览与播放
```

Python不只解析：原生HLS/DASH分片确实由嵌入yt-dlp/Python下载与有序组装，Kotlin负责直链HTTP、任务/取消桥、Android媒体处理和发布。把“全部字节都由Kotlin下载”写成架构是不准确的。依据：[ProtocolFileDownloader](../android/app/src/main/java/com/nanzhufeng/videodownloader/probe/ProtocolFileDownloader.kt)、[segment_transfer.py](../android/app/src/main/python/nanzhufeng_probe/segment_transfer.py)。

| 持久事实 | 所有者 / 用途 |
| --- | --- |
| media_items | 来源身份、标题/作者、图集信任合同、用户确认播放器与Referer |
| download_tasks | 选择、顺序、质量/命名/目录、状态、字节/速度、失败、连接/处理阶段、分段数 |
| download_history | 终态、输出URI列表、大小/存在标记、图集认证字段、收藏 |
| download_throughput_reports | 分阶段真实网络/提交字节、平均/峰值、连接、重试/回退及结果 |
| DataStore settings | 默认质量、输入草稿、目录、自动网络恢复、并发任务数、命名规则 |
| 播放SharedPreferences | 以成品URI摘要为键的位置，不改变历史、任务或输出URI |
| 私有WebView/会话文件 | 导入及站点作用域Cookie；存在标记不等于真实账号动作已验证 |

Room数据库版本11，AppContainer登记1→11全部显式迁移，无生产破坏性回退：v2补失败原因、v3命名、v4连接/吞吐表、v5处理阶段、v6多URI/分段、v7至9图集来源/认证、v10捕获视频、v11收藏。依据：[数据库与迁移](../android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/NanzhufengDatabase.kt)、[Entities](../android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/entity/Entities.kt)、[DAO](../android/app/src/main/java/com/nanzhufeng/videodownloader/data/database/dao/DownloadDaos.kt)。

普通任务阶段为WAITING→PARSING→DOWNLOADING→VALIDATING→COMPLETED，同时有PAUSED、WAITING_NETWORK、FAILED、SKIPPED、CANCELLED。普通转换由[TaskTransitionPolicy](../android/app/src/main/java/com/nanzhufeng/videodownloader/core/model/TaskTransitionPolicy.kt)校验；批量暂停、取消、恢复经Repository的专用DAO更新，不假称每条SQL都调用同一函数。历史删除按当前语义删除记录、不删除成品；用户删历史后允许重新下载。依据：[Repository](../android/app/src/main/java/com/nanzhufeng/videodownloader/data/repository/RoomDownloadRepository.kt)、Git `c93e2e4`。

发布与Room归档是先后步骤，未构成跨系统原子事务。MediaStore/SAF当前在单次异常时清理本次新建目标，完整处理结束后才归档；不能由此推断突然进程死亡也有完整跨系统回滚。

## 6. 核心模块与所有权

| 模块 | 当前职责 | 约束 / 证据入口 |
| --- | --- | --- |
| UrlClassifier / PlatformSourceDiscoveryEngine | 官方链接、短链、批次/合集、失败/需捕获结果 | 独立来源身份；B站UP主页拒绝；同来源Generic快照有限复用 |
| YtDlpProbe / 平台元数据客户端 / youtube_probe.py | Kotlin/Python桥、轻量目标元数据、下载时完整提取 | API缺字段保未知，元数据结果不能伪造媒体源；[读取报告](verification/2026-10-10-cross-platform-read-performance.md) |
| DouyinProbeActivity / CaptureStore / CapturedMediaRepository | 目标网页视频或完整原图列表 | `/note/`结构化urlList、声明数量/来源版本/水印URL门禁，不接受推荐作品 |
| GenericWebProbeActivity / GenericWebCapture / web_media / web_collection | 可见目标播放器、用户确认iframe、合集成员与URL身份 | 不以任意网络请求兜底；旧身份仅精确来源兼容；[网站报告](verification/2026-10-08-website-parity-code-and-emulator.md) |
| SessionAccessPolicy / WebViewSessionProvider / android_network.py | 会话存在性、站点及目标主机凭据、Android代理 | 不向不相关主机泛化Cookie；YouTube导入Netscape文件，其余有受限登录/导出/清除入口 |
| RoomDownloadRepository / DownloadTaskRunner | 入队、去重、恢复/重试、阶段、发布后归档 | Gallery认证/旧媒体可读性独立检查；重试保身份；[任务运行器](../android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DownloadTaskRunner.kt) |
| DownloadEngine / ForegroundDownloadWorker | 开始/暂停/停止、有限任务lane、恢复、真实后台状态 | 通知/窗口异常不取消传输；调度受系统/OEM限制；[后台合同](background-download-island.md) |
| PlatformTransferPolicy / TransferConcurrencyBudget / HttpFileDownloader | 有界Range、能力校验、旧片保全、恢复/冷却、真实吞吐 | 全局预算与每流连接不同；不承诺全平台相同吞吐；[传输合同](download-transfer-policy.md) |
| ProtocolFileDownloader / segment_transfer / ts_timeline | HLS/DASH有序组装、提交检查点、目标独占、声明切换与AVC排空 | 不将请求进度当提交游标，不更改原失败源；其他编码需另验 |
| Media3TrackMuxer / AndroidStreamRemuxer | 独立轨和TS压缩样本封装、AAC拆分/去ADTS、时基/结束时间 | TS由MediaExtractor提取、Media3写入，不隐式重编码；[146验收](verification/2026-10-11-release-146.md) |
| AndroidPcmDecoder / Mp3AudioTranscoder / LameMp3Encoder | PCM解码、流式MP3编码、音频分段 | 真实格式和实际样本校验，不改后缀冒充MP3；Git `afccbe4`、`3888b7d` |
| VideoResolutionTranscoder / VideoSegmenter | 需要时分辨率转码；关键帧视频分段 | 无损分段边界允许GOP误差，不承诺精确数学等分；[分段报告](verification/2026-07-27-android-v1.2.3-video-segmentation.md) |
| MediaFileValidator / MediaStoreOutputStore | 结构预检、命名冲突新副本、MediaStore/SAF写入/回读 | 应用结构预检不等于每个媒体逐帧完整解码；本次异常只回滚新输出 |
| HistoryScreen / HistoryMediaMetadata / HistoryMediaLauncher | 筛选、元数据、收藏、失效、批量删除与播放路由 | 大小/时长优先从成品读取；[收藏合同](history-favorites.md) |
| NanzhufengApp / VideoPlaybackMemory / GestureController | 根播放会话、位置记忆、连续跳转/原生按钮 | 位置与下载完整性无关；[播放合同](video-playback.md) |
| DownloadIslandState / Overlay / core/ui IslandFeedback、IslandMotion | 后台常驻状态与App内短反馈分别拥有单一渲染器 | 简洁/详细模式、原位缩放、生命周期清理；[反馈UI合同](design/2026-10-10-island-feedback-ui-contract.md) |
| SettingsScreen / SettingsRepository | 质量、命名、目录、并发、会话/权限、版本 | DataStore默认并发任务1、支持1至3；设置存在不等于权限已授权 |

## 7. 关键决策及原因

| 决策 | 原因与取舍 | 依据 |
| --- | --- | --- |
| Android与旧桌面隔离维护 | 平台容器、存储、会话与打包链不同，混改无法正确验收 | AppContainer、Gradle、Git `cee7de0`/`4077045` |
| 持久任务与显式迁移 | 进程死亡/网络中断时需恢复用户选择、失败与旧历史 | Room Repository、数据库迁移、任务恢复测试 |
| 来源身份和作用域优先 | 泛化解析可能选推荐视频、水印变体或其他作者 | CaptureStore、SessionAccessPolicy、发现/解析测试 |
| 状态与UI反馈分离 | 界面加载、网络字节和阶段100%不能表示真实成品完成 | DownloadTaskRunner、DownloadOverallProgress、后台/反馈合同 |
| 真实MP3编码、必要时转码 | 容器/扩展名不能证明编码兼容；音频需可实际播放 | JNI/LAME链、Mp3FileValidator、Git `bab5b49` |
| 关键帧视频分段不重编码 | 保载荷并降低处理成本；均分边界不精确到任意帧 | VideoSegmentPlanner/VideoSegmenter、分段报告 |
| 有界多连接及旧断点布局独立 | 追求吞吐但不能抢占其他任务或因改策略删已下片 | PlatformTransferPolicy、预算/HTTP/分片测试 |
| 源时间轴与精确样本边界 | 系统封装器、AAC拆分、B帧和时基取整曾损失样本/漂移 | AacSampleTimeline、TrackEndTime、CompressedSampleBuffer、146报告 |
| 播放会话与筛选提升到稳定根层 | 导航/折叠切换或旧回调不能错播、跳回旧位置或丢筛选 | NanzhufengApp、播放器/收藏测试与合同 |
| 输出命名新副本、失败回滚新对象 | 历史去重与用户删除后重下都不能覆盖旧成品 | OutputFilePolicy、MediaStoreOutputStore、Git `c93e2e4` |
| 原图标/字体许可保真 | Android图标容器适配不应增加白底/外框；字体保持许可与原始字节 | res/mipmap、assets/fonts、Git `97e1b1e` |
| 证据分层、冻结交付 | 相同版本重构建仍可能变字节；安装/构建不是业务成功 | tools/verify_frozen_release.py、146产物清单与检查点报告 |

## 8. 开发过程与Git脉络

以下Git日期来自可达提交；没有独立提交的阶段按验证记录日期标注，不能把报告日期当提交日期。开始时114条历史全部纳入索引与变更路径统计，核心里程碑如下。

| 时间 | Git或记录 | 演变与边界 |
| --- | --- | --- |
| 2026-07-15 | `4077045` | PySide原型、浏览器会话/抖音捕获、桌面下载与最初测试 |
| 2026-07-16 | `cee7de0`、`c1f8db0`、`738b149` | Android可行性、Chaquopy运行时及公共媒体写入；随后Room/三页壳 `8b8d729` |
| 2026-07-16至18 | `afccbe4`、`bab5b49`、`fc3f276`、`2b8db69` | 原生MP3、正式发现/队列、折叠工作台、品牌与可靠下载checkpoint；2b8db69日期为7月18日 |
| 2026-07-21 | `5949206`、`399814c`、`ba40b76` | 键盘遮挡恢复、1.0.0构建与最终回归经验固化 |
| 2026-07-26 | `5d7f72e`、`00d05bb`、`e1be891` | B站/小红书、媒体元数据从详情提升到历史摘要及其证据 |
| 2026-07-27 | `4fe4db8`、`3888b7d`、`4598f39`、`e9e8360`、`8f851e4` | 音频回退/播放、音视频分段、历史批量删除、快速合并 |
| 2026-08-02 | `c93e2e4`、`457ee62` | 删除历史后重下、抖音短链受控解析 |
| 2026-08-21至24 | `1bdcc11`、`69a313b`、`8377cb4`、`dcfe85a`及冷启动报告 | 历史媒体/播放器、图集来源、传输完整性、Compose CREATOR/R8冷恢复 |
| 2026-09-01至17 | `2130e1b`、`d9a66a1`及对应报告 | 工作台/导航/播放器整理与设置关于信息 |
| 2026-09-23至10-08 | 发现/设备记录与网站范围方案 | 会话事实澄清、Pornhub/Generic/合集/协议/动态捕获及Room10；后于d0602d1统一纳入Git |
| 2026-10-09 | 樱花/身份/停止及网站A–D记录 | 原失败保全、AAC/TS时间轴、重复片恢复、真实成品/解码/历史冷进程重开 |
| 2026-10-10 | 多平台读取、速度/稳定性、后台岛和播放记录 | SSR/API元数据路径、有界Range/协议并发、真实性能分层、简洁/详细岛、播放记忆/手势；外部指标未全面达标 |
| 2026-10-11 | `d0602d1`、`70cbcca`、`110ccb5`、`156f6b2` | 收藏/Room11、146续传与封装追补、正式发布/清单回读、本地最终回归/合同与经验增量 |

## 9. 踩坑、根因与修复

| 失败 | 根因 / 首个分叉 | 修复与证据边界 |
| --- | --- | --- |
| 智能读取慢、Cookie有却读失败 | 目标视频复用图集桌面UA、等待播放器/重复完整提取；存在Cookie被误当可用会话 | 移动目标视频、受限SSR/元数据快路径；[原生抖音读取](verification/2026-10-10-douyin-first-read-under-one-second.md)、[会话记录](verification/2026-09-23-android-v1.2.91-douyin-session-discovery.md)，每次≤1秒未全面通过 |
| 图集少图、水印、空/截断WebP | 目标原图被首图/其他字段替换；签名图并发/206提前终止 | 结构化来源版本、精确数量、顺序图传输、RIFF/最终URL校验；[图集验收](verification/2026-08-23-android-v1.2.82-douyin-gallery-integrity.md) |
| Generic无格式或合集全被过滤 | codec键存在但值None；成员webpage_url共用合集页 | 明确未知codec，成员优先独立媒体来源；网站代表样本通过，不推断任意网页 |
| 网页可播但无法捕获 | Shadow DOM和跨域iframe不在普通DOM扫描范围 | 开放Shadow DOM扫描、用户确认目标iframe；封闭/复杂环境不宣称覆盖 |
| WebM强制MP4封装失败 | VP8/Vorbis不适合旧MP4路径 | 保留完整单文件容器/MIME；WebM多段仍拒绝，见网站范围报告 |
| Pornhub公开来源410 | 嵌入Python与主机网络实现结果不同 | 限官方单作品/CDN兼容请求；[下载记录](verification/2026-10-09-pornhub-download-playback-and-navigation.md)，非全站通行保证 |
| 暂停/恢复重复片、错误检查点 | 同目标旧尝试未退出；并行回调进度领先有序追加 | Kotlin/Python目标独占、按已提交片保存游标；强制乱序/缺片重试回归；原坏缓存不改写 |
| 樱花writeSampleData异常与早期缺帧 | AAC负PTS钳零、PES多帧、声明切换/重叠、AVC末访问单元未排空 | 时间轴/每帧AAC/有证明重复恢复，合格URI125全片解码与历史重开；早期URI120不计合格，见[樱花闭环](verification/2026-10-09-sakura-remux-identity-and-stop-closure.md) |
| 长视频能解码但音频漂移 | 固定时基中逐样本取整累计 | 绝对PTS对齐；失败URI180保留、合格181按源包/时间轴验证；[稳定性第二审计](verification/2026-10-10-download-speed-stability-second-audit.md) |
| 146前候选能解码却缺头尾 | 连续TS未排空，系统MP4 B帧edit list裁帧 | Media3压缩样本、CSD/切片/EOS；143为26/30、145为28/30、146两条30/30及源像素一致，见146报告 |
| 合格小媒体被拒 | 64KiB硬门槛当格式保证 | 有界容器结构检查，实际短MP4/WebM下载/播放；[短媒体报告](verification/2026-10-10-island-feedback-and-short-media.md) |
| 下载停滞与单连接回退 | Range失败/EOF、阻塞Call取消不及时、服务端限流、旧布局与连接预算混淆 | 能力探测、保前缀续传、取消监视、冷却和有界并发；[生命周期报告](verification/2026-10-10-download-performance-and-lifecycle.md)、传输合同 |
| 冷启动崩溃 | R8移除Compose保存状态CREATOR初始化 | 精确keep及Release mapping门禁；[冷启动记录](verification/2026-08-24-android-cold-start-parcelable-state.md)，覆盖仍不等于用户冷启动验收 |
| 后台无岛/旧窗口覆盖新任务 | 进程importance误判界面、Worker代次/权限与生命周期 | Activity可见性及单队列状态、窗口失败与下载隔离；权限/OEM须另验 |
| 合并/保存看起来卡在校验 | 处理/复制阶段不分，传完字节便看似完成 | 明确真实阶段和发布字节进度；终态只从结果发布/归档读取 |
| 宿主测试改变候选APK | Python导入打包依赖时写入宿主pycache | 精确保全/移出污染文件、禁写字节码、冻结检查器；不清用户缓存、不重建冻结146 |

列表摘要、折叠布局和图标也有历史反馈：时长/大小应从成品惰性读取并固定行高；软键盘后主动作必须可达；图标不追加底板/外框。这些沿用[历史信息审计](verification/2026-07-26-history-list-media-metadata-experience-audit.md)、[键盘审计](verification/2026-07-21-keyboard-final-regression-experience-audit.md)及对应Git，而非复制当时像素/容差作为通用规则。

## 10. 测试与验证证据

| 层 | 已有结果 / 适用范围 | 不能替代 |
| --- | --- | --- |
| 测试源码 | 79个JVM Kotlin文件、13个Python测试文件、1个JS测试文件；另有合成服务/媒体夹具。androidTest为历史资产 | 数量不表示用例通过或全覆盖 |
| 自动门禁 | 146生产基线Debug/Release各443、Python120、JS4；此前检查点已实际重跑，本轮只复核适用性 | 设备、账号、真实服务、吞吐 |
| lint/构建/R8 | 146报告0E/33W/3I、签名Release/R8通过，本轮不重建 | 警告不等于已消除；打包不证明业务 |
| 网站/取消 | A–D受控代表样本、通用旧身份兼容/路径端口分离、樱花该集与传输中取消，见网站审计/樱花记录 | 全部站点/所有作品/OEM |
| 五平台真实媒体 | 各阶段有抖音、YouTube、TikTok、Pornhub代表成品；B站先DNS失败后103真实传输/104冷历史验证，见[生命周期报告](verification/2026-10-10-download-performance-and-lifecycle.md) | 不是146重新下载全部平台 |
| 长片/协议完整性 | 127源时间轴与包数、146两条受控HLS源像素/完整AV解码通过，缺片恢复走原任务正常UI | 其他编码、全部容器、所有长片 |
| 播放/岛/收藏UI | 各对应阶段正常UI证据与合同保留；146收藏及受控成品证据见发布报告 | 未重跑项不标为146新设备通过 |
| OPPO | 142同签名保数据覆盖/回拉哈希与安装身份已核验；146未覆盖/操作业务 | 私有Room/收藏播放用户路径、OEM长期后台 |
| connected/仪器 | 永久禁止，源码仅历史保留 | 不能为补测试数量而运行 |

本轮文档复盘没有重跑公网、播放器或主手机。依据：[146发布](verification/2026-10-11-release-146.md)、[本地回归](verification/2026-10-11-checkpoint-experience-increment.md)、[OPPO142覆盖](verification/2026-10-11-oppo-release-142-overlay.md)。原失败/合格样本都保留适用版本、来源与内容边界，不被新文档刷新运行时间。

## 11. 构建、部署与交付

Android在 `android/` 使用wrapper和匹配JDK构建；macOS现有门禁使用Android Studio JBR。Python构建器可由 `nanzhufeng.buildPython` 或 `NANZHUFENG_BUILD_PYTHON` 指定。NDK/CMake、SDK与依赖匹配当前配置。旧桌面另用根requirements、start.py和PyInstaller脚本，依赖FFmpeg/Playwright等外部环境；本轮未确认其可安装或现代桌面版本能力。

常用**未冻结新增量**命令：`./gradlew testDebugUnitTest testReleaseUnitTest lintRelease assembleRelease`；正式归集任务 `stageFormalReleaseArtifacts`依赖APK/AAB构建。版本化中文APK/AAB与GitHub英文APK名称属于交付命名，不能因此重签或生成两个不同包。本轮冻结146只运行只读核对；需要真实重跑单测时可用 `tools/rerun_unit_tests.gradle`，不得执行产物生成来覆盖已冻结包。

- 正式签名执行合同：完整环境组优先、完整App专属用户Gradle组备用、禁止混配；**实际源码** `releaseSigningValue`按字段回退，仅检查最终齐全。部分环境+备用值可能通过，整组拒绝门禁尚缺；文档不能把要求写成已实现。
- `packageRelease`/`bundleRelease`检查最终字段与keystore文件；`assembleRelease`还检查Parcelable映射；所有connected测试有失败关闭门禁。依据：app Gradle及[Proguard](../android/app/proguard-rules.pro)。
- 最近146门禁曾使用 `--offline --dependency-verification=off`，现有校验XML包含531个组件，但严格依赖校验是否全面恢复未确认。不得写成全套依赖校验已通过。
- 覆盖安装只在目标、用户授权、递增版本、同证书、正式非Debug包及数据指纹核对后用 `pm install -r --user 0`；禁止卸载/清数据/数据库注入，安装不替代业务验证。
- 正式发布冻结顺序为最终构建→字节/身份→精确安装/回拉→交付校验；此后不重建/重签同一冻结包。146 APK57,718,955B与SHA记录见artifact；GitHub回读是既有发布报告证据，本轮未刷新远端。

## 12. 文档与源码冲突处理

| 冲突 | 当前依据 | 本轮处理 |
| --- | --- | --- |
| 旧档案的main/d9a66a1、105提交、55 JVM/7 Python等被当现状 | 当前Git114历史、清单与146测试记录 | 日期化旧证据，当前基线/清单独立列出 |
| 旧档案把2b8db69列为7月21日 | Git提交日期7月18日；1.0.0构建399814c为7月21日 | 更正里程碑，不混报告日期 |
| 目录把test/androidTest放在src/main下 | 实际src/test与src/androidTest | 重写真实目录树 |
| “最终字节全由Kotlin负责” | ProtocolFileDownloader桥接Python下载/组装 | 明确直链与原生协议分工 |
| TS仍由旧系统封装器处理、部分签名环境必失败 | AndroidStreamRemuxer已用Media3；签名实际字段回退 | 修正所有者与过度门禁声明，缺口保留 |
| 旧ChatGPT开发上下文以1.2.82/8149cac称当前 | 当前146与生产提交；该文件是8月历史 | 加历史快照路由，原正文保留 |
| README旧版本冲突表继续要求同步 | README已146；原冲突属于10月8日前 | 不重复已完成同步，不说旧差异仍存在 |
| 旧网站记录B站DNS红/樱花缺口，被当永远未完成 | 后续103/104及樱花URI125独立验证 | 先失败后修复按样本/版本并列，不丢弃失败证据 |
| 本轮状态把旧UI边界合称146重跑 | UI报告各有阶段；146重点是收藏/HLS | 状态注明沿用/未重跑，不改变旧证据 |

事实取证顺序：当前代码/配置→适用测试与产物/设备证据→Git和验证文档→计划/旧截图/记忆。事实优先级不覆盖用户指令和安全授权。

## 13. 已知问题、未确认事项与后续路线

### 已确认问题

1. 签名配置未自动整组选源/拒绝来源混配；有源码依据，待单独生产增量与回归，本文不改业务代码。
2. 固定全平台每次首次读取≤1秒未全面通过，119有1.067/1.940秒等超标；公网长片约4.79MB/s仍未达5–10MB/s。其原因包括外部网络与平台条件，不能用本地峰值、读取缓存或合并降时替代实测。
3. lint33条警告仍在，完整依赖校验路径尚未证实恢复；不能将“构建通过”写成零风险。

### 尚未确认

- AVC之外TS、更多长片/独立轨、旋转/色彩等元数据、极大历史量与复杂自定义目录压力。
- 发布与Room归档之间突然死亡、MediaStore/SAF多输出跨系统恢复；当前异常回滚不是进程死亡的完整证明。
- OPPO146用户路径、私有Room迁移/收藏播放、OEM长期后台、锁屏/通知权限/TalkBack和窄屏多任务空间。
- 更多真实作品/站点、设备级重启及旧桌面原型在当前机器的完整运行/打包；不以代码/历史单例补造结论。

路线按风险和证据推进：先签名组门禁与严格依赖校验；再复现具体来源/格式的读取、吞吐、完整性或发布恢复缺口；最后只在明确授权下做主设备业务/长期后台验收。每个增量先建立红灯、指定唯一所有者、最小充分回归，再更新状态与证据，不重复已通过且源码未变的工作。

## 14. 维护与复用入口

固定规则看[根AGENTS](../AGENTS.md)；五类独立Skill位于 `.agents/skills/`，由AGENTS路由。通用原则、项目专属内容和不可照搬项看[可迁移开发经验](可迁移开发经验.md)。本次范围、结构检查和保全证明看完整复盘记录；146正式经验矩阵保留于[增量审计](verification/2026-10-11-app-experience-increment-audit.md)，无需复制共享规范正文。
