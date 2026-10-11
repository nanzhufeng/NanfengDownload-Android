# 南枫下载 Android 当前交接

更新时间：2026-10-10 00:35（Asia/Shanghai）。本文件为当前任务唯一交接正文；接手时20:55正文保留在 `docs/archive/PROJECT_HANDOFF_BEFORE_20261009_SAKURA_CLOSURE.md`，更早正文见此前归档，不把旧测试数/APK哈希/红灯当作现状。

## 当前结论

最新用户要求读取提速：原生官方短链头解析、2分钟/32条身份映射缓存、WebView正常HTTP缓存、2分钟同目标已验证结果复用已实施。正常UI车子定损基线16.55秒；最终重载网页10.91秒（身份映射已缓存），复读0.11/0.09秒；另一条果蝇短链首次在当前进程解析并加载14.42秒，复读0.09秒。首次网页仍慢，不能声称全部秒读。测速已校准设备时钟；异常输入/流读取漏判排除。前后27队列/22历史、目标各一条PAUSED/0B、活动0，旧URI114/115/125保留。详见 `docs/verification/2026-10-10-douyin-read-speed.md`。本轮仅读取提速，不把它当作下面尚未通过的真实下载验收；无主设备覆盖授权。

当前抖音问题：用户两条短链作品7684884186910329202（车子定损）、7690158572814077184（果蝇神经元）在桌面探测页均超时；移动页对比能返回目标HTTPS源。此前只看早期快照、漏掉后续成功捕获的结论已更正。最小修复视频使用实际引擎移动UA，图集继续桌面完整性契约；最新包正常UI两条读取通过、未重复入队。真实成品未通过：第一条传输0B，主机同源CDN HEAD发生TLS EOF，但未取得Android最终失败栈，不能称已证明应用同一根因。最终两条PAUSED/0B、活动任务0，无目标历史/URI。优先继续查传输并完成真实下载/解码/播放，见 `docs/verification/2026-10-10-douyin-mobile-video-probe-regression.md`。截图设备版本和会话可播放性仍待用户回复，不操作主设备。

本轮指定的四项追补已经完成，范围仅隔离 AVD `emulator-5580 / NanfengWebParityTmp`：

1. 原樱花失败缓存、数据库/WAL、报告与日志先保全，再证实 AAC 起始时间、TS 时间轴/连续性计数及 AVC 尾帧问题；实站续传又捕获完全相等的重复分片，先失败拒绝发布，再修复。
2. 最新网址身份包正常 UI 重读旧 MP3，没有新任务/历史，URI仍为1000000114；旧DASH URI1000000115保留。8766/8767同basename、8767同端口不同路径分别正常入队。
3. 最终包正常传输中停止：任务f3513193-0517-470b-b707-1b8867e5bd8d，在读数8704 B且DOWNLOADING后正常点击停止，收束为CANCELLED、23040 B，无输出URI、fileSize=0、无待发布成品。0 B早期取消和操作前已完成的短样本都不代替这项证据。
4. 樱花同一第1集的实站任务ac701443-dfc4-4abd-a4c8-ee9318b3a462，经正常UI重试恢复已保全的真实缓存，最终COMPLETED：URI `content://media/external/video/media/1000000125`，162,297,814 B；H.264主体1920×810/AAC 44.1kHz双声道，1229.886秒，30,747个视频采样/52,912个AAC采样，全片音视频-xerror解码exit0、stderr0B。AAC负载逐一与恢复源相等；正常历史实际画面和冷进程重开后持续提交视频帧通过。

**A–D不能外推成全部电脑版站点/作品或OPPO都已验收。** 更多站点/Pornhub作品、cn独立Android下载、其他樱花剧集、设备级重启和OPPO用户路径仍未逐个验证。原12集列表、过滤、顺序和捕获证据沿用既有记录，不重复已通过工作。

## 当前源码、包与验证

- 路径 `.`，HEAD仍为d9a66a1/main；接手94个改动/未跟踪路径全部保留，本轮没有提交、暂存、推送或发布。
- Android `1.2.97 / 10297`，Room 10。没有持久字段、schema或migration变化。
- 最新本地签名APK `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.97.apk`，SHA-256 `c9e2829cdc39f70539faecc8e8f2914f7b90666db0c6711dd609b1cc9189af89`；AVD base.apk回拉相同。证书SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。仅隔离验收包，不是递增版本主设备交付。原四项闭环APK哈希09b8748d…、移动读取修复包16ba43a8…保留在各自验证记录及私有回滚副本，不重算原证据。
- 最新Debug/Release JVM各326项通过；lint0 errors/35 warnings/3 information，Release/R8 Parcelable CREATOR门禁及assembleRelease通过。此前Python92项未重复运行，本轮未改Python。临时沿用离线 `--dependency-verification=off`，未改既有校验元数据。构建、入队、传输字节没有作为业务完成证据。
- 修复入口：`AacSampleTimeline.kt`、`AndroidStreamRemuxer.kt`、`ts_timeline.py`、`segment_transfer.py`、`ProtocolFileDownloader.kt`。保留原ADTS布局、来源隔离、generic完整URL身份及旧URL精确兼容；无新常驻按键、引擎升级或媒体后端。
- 同目标独占覆盖“分片→时间轴处理→封装→收束”；Python进度回调后再次检查取消。旧坏缓存仅当额外时钟切换、声明位置/总时长和相邻完整字节相等共同成立时恢复，明确声明的重复必须保留；不能证明就失败保全。TS限定单PCR时钟，AVC末帧处理不生成额外图像。

## 证据保全和禁止误用的中间结果

- 私有目录 `<private-evidence>/nanfeng-sakura-evidence-20261009/` 权限700，保留原6e808840…缓存、数据库/WAL、报告、日志、实站续传原始缓存、最终媒体与结构化验证输出；不得把媒体、清单临时地址、账号或会话材料提交。
- 原失败 `.segments` 169,753,472 B，SHA-256 `f93f7466a83fe71182c4286e70064665e80f54c91bc2ffddfeccdf5d2f2ba12a`。原 `.hls-plan.json` 32,789 B，SHA-256 `58c051cb2b958419092a9ca454853c54076bf13ef426f09322e09d654b5a0d32`。
- 原6e808840…中间输出URI120虽COMPLETE，但有边界缺帧，保留为证据，**不计最终完整验收**。最终真实任务使用同一公开第1集带验证查询的页面另建，避免删除旧记录或由旧成品去重跳过；正常网页播放/确认源和正常重试均有记录。
- 实站续传原文件 `fresh-real-cache/video.mp4.2e4b5f54d1ac75b5aa7b8387.segments` 170,168,012 B；相邻414,540 B完全重复、多2.4秒。清单410段、1229.88秒、3处声明位置1111.2/1128.84/1212.24。恢复输出只删去证明属于追加噪声的一份，原文件未动。
- 最终媒体 `sakura-real-final.mp4` SHA-256 `442b3e78ba24e1227edd525ae3724d44939cc7f1eabb99e25dc41922d21dcbb9`。控制回放URI121–124不是实站新传输证据；短停止对照df15474f…已完成为URI126，不能计取消通过。

## 隔离环境与收束状态

- 只可继续操作emulator-5580；OPPO和emulator-5592未操作。禁止所有connected*AndroidTest、数据库注入、卸载或清数据；没有主设备覆盖或远端授权。
- AVD PID41200，数据目录 `<private-evidence>/nanfeng-web-parity.KpOWcx/avd`，1920×1920/420dpi；AVD和正常MediaStore成品保留。收束时活动任务0，最终任务/取消态和旧MP3/DASH URI保留。
- 已有本任务HTTP服务器PID43595/8765、44099/8766、52428/8767仍存活；未关闭其他项目服务。复用前复核PID/命令。8767慢样本约每8192B延迟3秒，新合成20秒停止夹具在 `/slow/stop-long/release-hls.m3u8`，文件位于fixture根的 `stop-long/`。
- 本轮用于真实媒体回放的8个临时host清单/符号链接已精确移除；原媒体证据、AVD成品和原有夹具不删。UI XML及读取脚本在私有证据目录，不能整块输出含其他作品标题的XML；后续始终用最新语义bounds。
- Wi-Fi/移动数据和自动恢复未改，原网络等待/自动恢复开关验证沿用既有通过记录。进程重开通过，不声称AVD设备重启或OEM通过。

## 下一步安全入口

原四项红灯已闭环；当前优先排查抖音目标CDN传输并完成两条真实成品验证，不重下、清除或复写原樱花证据，不重复已通过流程。抖音第一任务73bfd20b-6ee9-47a1-bc35-964389b13021为PAUSED/0B/retry3，第二e2614ee5-c722-4428-9a5c-19efae738122为PAUSED/0B/retry0；保留任务，后续只正常UI恢复。正式递增版本、OPPO覆盖和GitHub发布须另获授权。

- `docs/verification/2026-10-09-sakura-remux-identity-and-stop-closure.md`：本轮根因、红绿回归、完整哈希、正常UI与成品/取消证据。
- `docs/verification/2026-10-09-website-parity-completion-audit.md`：最新A–D完成矩阵和未验收边界。
- `docs/verification/2026-10-09-network-and-long-hls-recovery.md`：此前网络验证及本轮续传追补。
- `docs/verification/2026-10-09-pornhub-download-playback-and-navigation.md`、`2026-10-08-website-parity-code-and-emulator.md`：已通过样本历史记录；包哈希属于各自阶段。
- 方案 `docs/superpowers/plans/2026-09-27-android-desktop-website-parity-plan.md`；稳定架构 `docs/南枫下载 Android 完整开发档案.md`。

下一条只读命令：`git rev-parse --short HEAD`、`git status --short | wc -l`，以及显式 `adb -s emulator-5580 shell pm path com.nanzhufeng.videodownloader`。先按根AGENTS和专项Skill收窄验证，不推送/发布/操作主设备。
