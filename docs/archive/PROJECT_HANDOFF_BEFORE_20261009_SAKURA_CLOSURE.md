# 南枫下载 Android 当前交接

> 合同层级：历史交接。原文保留当时目标/状态，不代表全部目标已完成；现行实现及未达目标以[合同总入口](../contracts/README.md)为准，不从本页旧参数重建功能。


更新时间：2026-10-09 20:55（Asia/Shanghai）。本文件是当前任务唯一交接正文；旧正文已保留在 `docs/archive/PROJECT_HANDOFF_BEFORE_20261009_FINAL_HANDOVER.md`，其中暂停进度、旧测试数与 APK 哈希不得当作现状。

## 目标、范围与当前结论

继续落实 `docs/superpowers/plans/2026-09-27-android-desktop-website-parity-plan.md` 的 A–D 网站范围对齐，同时保留既有抖音修复和用户数据。最新用户要求办理交接文档。本轮未授权 OPPO 安装、GitHub 推送或发布；未执行这些动作。

**整体尚未完成。** Pornhub 公开样本、受控音频/DASH、断网等待与恢复已在隔离模拟器验证；樱花真实长 HLS 最终封装失败，最新通用网址身份修复和正常 UI 停止仍需复验。不要重复已通过工作，也不要把构建、传输字节或入队说成下载完成。

## 当前代码与产物

- 仓库：`.`；分支 `main`；HEAD `d9a66a1`。交接修改前 94 个改动/未跟踪路径，包含前期 WIP；本轮没有新提交，不可重置或覆盖。
- Android 版本 `1.2.97 / 10297`，Room schema 10。最新身份兼容修复没有增加持久字段或迁移。
- 最新本地签名 APK：`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.97.apk`，SHA-256 `6161a6b5632f49cab5b67f701b46cdb00755382dc41ae116d47cf0521430ddb4`。**尚未安装到测试 AVD；不是可直接覆盖主设备的递增版本交付。**
- AVD 已安装的上一个生命周期/HLS 包：SHA-256 `4397b5c008989639904524ea38df12c3a514721656b7559e69beef80bca63cea`，交接时通过设备 `base.apk` 再次核验。不能用它证明最新身份修复已通过运行验收。
- 最近完整验证：Python 87 项；Debug/Release JVM 各 304 项；lint 0 errors、35 warnings；Release/R8 Parcelable CREATOR 门禁通过。构建会话 60217、Python 会话 91382 已成功结束，没有本任务构建仍运行。

## 已完成，接手不要重做

| 增量 | 代码与已获得证据 |
| --- | --- |
| Pornhub 请求兼容 | `pornhub_http.py` 仅限定官方 HTTPS 作品页及带已验证来源的 phncdn 传输；原问题是 Android yt-dlp 请求通道 410，不能再笼统解释成年龄确认。没有代用户确认年龄、登录或绕过访问限制。 |
| AAC 封装 | `AacAdtsSampleLayout.kt`、`AndroidStreamRemuxer.kt` 逐帧校验/剥除 ADTS、多帧拆样本。公开样本最终 720p：67,927,311 B，H.264 1280×720/AAC，约 360.94 秒，全片音视频解码成功，历史内置播放与冷进程重开通过。详细记录见同日 Pornhub 验证文档。 |
| 底部悬浮导航 | `navigation/NanzhufengApp.kt` 内外屏共享底部栏；外屏项目 52dp、内屏 56dp，额外底距 4/6dp，保留系统手势安全区、柔和阴影；标签首页/历史/设置。隔离 1080×1920 与 1920×1920、420dpi 已目视检查；不是 OPPO 验收。 |
| 正常队列暂停入口 | `FormalHomeSections.kt` 使用真实 pause/stop 回调；运行时底部变“暂停下载”，运行行删除槽变“停止下载”。樱花正常 UI 暂停在 42,569,464 B，5秒字节不变且无假完成；继续后超过 100MB。停止入口存在不等于停止行为已验收。 |
| 网络生命周期 | Repository/DAO/AppContainer/Worker/Runner/Engine 统一网络等待；只改变活动任务，不覆盖用户暂停或终态。AVD 自动恢复关闭：断网进入 WAITING_NETWORK，联网仍保持相同字节，明确开始才恢复；再次断网/联网后打开设置自动恢复，自动继续。网络和开关现均恢复开启。 |
| 长 HLS 原计划 | `segment_transfer.py` 将首次确认子清单保存在任务私有 `.hls-plan.json`；相同请求恢复不重新选择变化清单。请求改变不借旧计划，401/403/404/410失效后明确重试刷新；精确清理旧 Frag，封装成功清理计划。真实 yt-dlp/FFmpeg 回归通过；AVD 网络往返保持同一 a1 指纹。最终封装失败尚未解决。 |
| 受控音频与 DASH | 已安装 Release 正常输入/下载/历史播放：MP3 17,390 B，URI `content://media/external/audio/media/1000000114`，4.017052秒，播放器进度到00:04；DASH 160,020 B，URI `content://media/external/video/media/1000000115`，H.264 320×180/AAC，4.040272秒，播放器提交101帧。两文件完整解码通过。 |

## 未完成与新红灯（按接手顺序）

1. **樱花真实最终封装失败。** 原页 `https://www.yhdm.one/vod-play/2025999379/ep1.html`；任务 `6e808840-ec08-4e6d-bcc1-91953dfc078f`。20:55只读回查：`FAILED`、169,610,956 B、totalBytes=0、failureType=TRANSFER、errorSummary=`writeSampleData returned an error`；最新吞吐报告 `FAILED`、committedBytes=0、“分段下载或媒体封装失败”。没有最终成功成品，根因仍未确认。先保存本任务缓存/报告、定位封装样本/轨道/时间戳错误，再修复及完整下载、音画解码、历史重开。不要先重下或清缓存销毁证据。
2. **最新通用网址身份兼容仅自动测试通过。** GenericIE 的 basename id 使不同路径/端口碰撞。现在 generic 身份按完整输入 URL 派生，named extractor保持站点 ID；Python返回 legacy id/spec，Kotlin发现层保留临时兼容提示。Repository只有旧记录原始URL精确相同才沿用旧身份；Resolver仅同原页采用旧spec保留续传缓存，不改已有库。源码入口：`web_media.py`、`YtDlpMediaInfo`、`PlatformSourceDiscoveryEngine`、`HomeViewModel`、`RoomDownloadRepository`、`YtDlpTaskMediaResolver`。先在指定隔离 AVD 安装最新包，验证重读已完成的8765音频或DASH不会新增任务/历史、旧URI不变；再验证8767与8766同basename能分别入队。
3. **正常 UI 停止未验收。** 8766样本任务 `806041b3-f1e4-4087-bb04-7f028581ce67` 在点击停止前已经 COMPLETED（159,772 B、URI1000000116），不能记成取消通过。8767更慢样本被旧包误去重；修复包验证入队后立即按正常“停止下载”，要求取消态且无COMPLETED/新增成品。不要注入数据库或调用内部测试入口。
4. 其他代表性网站、Pornhub其他作品、cn站点独立Android下载、OPPO内外屏和真实用户下载仍未全部验收；不得宣称所有电脑版网站范围闭环。自动验证通过不能替代这些证据。
5. 完成以上再做 A–D 审计、正式递增版本/签名交付；覆盖安装与GitHub发布须获用户授权。旧抖音样本真机结果与缺少作者/日期元数据见归档和2026-09-23验证文档，保持不回归。

## 环境、存活进程与隔离边界

- 唯一允许本任务继续操作的测试设备：`emulator-5580`，AVD `NanfengWebParityTmp`。OPPO `<oppo-serial>` 已重新连接，**不要操作**；`emulator-5592` 属于别的项目。永久禁止任何 `connected*AndroidTest`。
- AVD进程 PID 41200，启动会话8970；无窗口、无snapshot。注册文件 `<local-reference>`，数据目录 `<private-evidence>/nanfeng-web-parity.KpOWcx/avd`。保留该AVD及测试媒体以接续验证。
- 本任务服务器：PID43595 /8765 /会话15802（受控夹具HTTP）；PID44099 /8766 /会话57542（慢分段）；PID52428 /8767 /TTY会话46976（每8192B延迟3秒）。20:55均仍存活。接手先复核PID和命令再使用或精确关闭。其他项目4178服务不在范围内；禁止宽匹配kill。
- fixture根 `<private-evidence>/nanfeng-web-parity.KpOWcx`；慢样本 `http://10.0.2.2:8767/slow/release-hls.m3u8`，目前输入框保留该链接及旧包重复提示。Wi-Fi、移动数据、自动恢复开关均已恢复ON；操作前再次读取状态。
- AVD界面当前1920×1920/420dpi。输入长文本使用正常输入框；ASCII Ctrl+A可用 `input keycombination 113 29` 后keyevent67清空。多行框MOVE_END并不等于全文末尾。每次用最新UI XML语义bounds定位，勿复用旧坐标。UI XML可能含成人作品标题，不整块输出或截图展示。
- 临时人工验证文件 `<private-evidence>/nanfeng-audio-final.mp3`、`<private-evidence>/nanfeng-dash-final.mp4` 是合成媒体；还有本任务UI XML与远程清单待精确清理。不得提交临时数据、签名媒体URL、Cookie或成人媒体。清理时只处理已确认本任务路径，不清用户数据，不清测试AVD证据。

## 接手第一条安全命令与证据入口

先读取本文件、根 `AGENTS.md` 和 `.agents/skills/` 对应开发/排错/测试流程；在项目目录执行只读核验：

```sh
git rev-parse --short HEAD
git status --short | wc -l
<local-reference> devices -l
shasum -a 256 android/app/build/outputs/apk/release/南枫下载-Android-v1.2.97.apk
```

后续设备命令始终显式 `-s emulator-5580`；先保全樱花失败证据，再按上方顺序操作。Android/验签使用Android Studio JBR，正式凭据只从既有完整环境或用户级App专属Gradle配置读取，不输出密钥。

- `docs/verification/2026-10-09-website-parity-completion-audit.md`：当前A–D完成矩阵。
- `docs/verification/2026-10-09-network-and-long-hls-recovery.md`：生命周期红灯、修复与运行追补。
- `docs/verification/2026-10-09-pornhub-download-playback-and-navigation.md`：历史公开样本闭环，文内APK哈希属于当次验收包。
- `docs/verification/2026-10-08-website-parity-code-and-emulator.md`：动态网页、合集、迁移等此前证据。
- `docs/南枫下载 Android 完整开发档案.md`：稳定架构与开发历史；最新动态以本交接及验证记录为准。

本次仅收束交接，未新开线程、未改业务代码、未重跑完整测试、未安装、未提交或发布。上下文门禁已为HANDOFF；接手应换新线程继续，不扩展当前长线程。工作流审计无硬错误；全局AGENTS体积超软限和部分既有Skill缺UI元数据属于外部软警告，本轮不改。
