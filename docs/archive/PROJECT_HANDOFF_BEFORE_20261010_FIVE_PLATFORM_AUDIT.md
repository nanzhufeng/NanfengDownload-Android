# 南枫下载 Android 当前交接

> 合同层级：历史交接。原文保留当时目标/状态，不代表全部目标已完成；现行实现及未达目标以[合同总入口](../contracts/README.md)为准，不从本页旧参数重建功能。


更新时间：2026-10-10 09:54（Asia/Shanghai）。本文件为当前任务唯一交接正文；接手时20:55正文保留在 `docs/archive/PROJECT_HANDOFF_BEFORE_20261009_SAKURA_CLOSURE.md`，更早正文见此前归档，不把旧测试数/APK哈希/红灯当作现状。

## 当前结论

最新用户要求首次智能读取一秒完成与风险审查：当前两条抖音样本最终正常UI通过。车子定损0.729秒、果蝇0.702秒（点击到首页入队/重复检查结束），分别捕获0.584/0.563秒，均真实HTTP请求、无近期结果复用。启动后台HEAD预热连接、原生目标SSR解析、现有平台会话作用域复用与无Cookie文件IO头读取已实施。修复缓存note绕过、账号/UA/过期源、媒体URL伪匹配、SPA实际页面绑定、原生取消/晚到回调、下载兜底取消/换号校验、同账号Cookie续期误判、IES域会话遗漏。详见 `docs/verification/2026-10-10-douyin-first-read-under-one-second.md`；旧00:35阶段与完整旧正文保留在 `docs/archive/PROJECT_HANDOFF_BEFORE_20261010_DOUYIN_NATIVE_FAST_READ.md`。不要把缓存复读或元数据完成称为真实下载完成。

最终仅5580验收包：1.2.97/10297、Room10，无schema/migration变化；APK与回拉base SHA-256 `aac978994f128f8e8293e7aad02c577a239c09ea5895f1b5a1ec2f8c0edbfca6`，证书c4fb47e…4547。Debug/Release各340项、JS4项通过；lint0Error/35Warning/3Information、Release/R8/构建通过。前后27任务/22历史、活动0；两目标仍各一条PAUSED/0B；旧URI114/115/125与大小保留。原Sakura证据和其最终解码/历史重开不重跑。OPPO、全新空访客会话、更多作品/新增Room记录的一秒指标仍待验证；第三方异常或无访客详情会正常网页回退，不承诺所有条件一秒。

两条抖音真实成品下载/解码/播放未通过，本轮没有恢复任务。当前来源已换成目标结构化声明的官方播放入口；旧CDN TLS EOF只属旧时间/旧源证据，不能据HEAD称传输根因已全部解决。保留任务73bfd20b…与e2614ee5…；后续只能正常UI恢复指定任务，避免全局开始误恢复其他已选失败任务。没有主设备覆盖或远端授权。

本轮指定的四项追补已经完成，范围仅隔离 AVD `emulator-5580 / NanfengWebParityTmp`：

1. 原樱花失败缓存、数据库/WAL、报告与日志先保全，再证实 AAC 起始时间、TS 时间轴/连续性计数及 AVC 尾帧问题；实站续传又捕获完全相等的重复分片，先失败拒绝发布，再修复。
2. 最新网址身份包正常 UI 重读旧 MP3，没有新任务/历史，URI仍为1000000114；旧DASH URI1000000115保留。8766/8767同basename、8767同端口不同路径分别正常入队。
3. 最终包正常传输中停止：任务f3513193-0517-470b-b707-1b8867e5bd8d，在读数8704 B且DOWNLOADING后正常点击停止，收束为CANCELLED、23040 B，无输出URI、fileSize=0、无待发布成品。0 B早期取消和操作前已完成的短样本都不代替这项证据。
4. 樱花同一第1集的实站任务ac701443-dfc4-4abd-a4c8-ee9318b3a462，经正常UI重试恢复已保全的真实缓存，最终COMPLETED：URI `content://media/external/video/media/1000000125`，162,297,814 B；H.264主体1920×810/AAC 44.1kHz双声道，1229.886秒，30,747个视频采样/52,912个AAC采样，全片音视频-xerror解码exit0、stderr0B。AAC负载逐一与恢复源相等；正常历史实际画面和冷进程重开后持续提交视频帧通过。

**A–D不能外推成全部电脑版站点/作品或OPPO都已验收。** 更多站点/Pornhub作品、cn独立Android下载、其他樱花剧集、设备级重启和OPPO用户路径仍未逐个验证。原12集列表、过滤、顺序和捕获证据沿用既有记录，不重复已通过工作。

## 当前源码、包与验证

- 路径 `.`，HEAD仍为d9a66a1/main；接手94个改动/未跟踪路径全部保留；目前122个路径，无提交、暂存、推送或发布。
- Android `1.2.97 / 10297`，Room 10。未改Room entity/schema/migration；SharedPrefs捕获新增UA/会话指纹，旧值兼容读取但不快速复用。
- 最新本地签名APK `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.97.apk`，SHA-256 `aac978994f128f8e8293e7aad02c577a239c09ea5895f1b5a1ec2f8c0edbfca6`；AVD回拉相同。证书SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。仅隔离验收；旧包哈希属于各自阶段并有私有回滚副本。
- 最新Debug/Release JVM各340项、JS4项通过；lint0 errors/35 warnings/3 information、Release/R8门禁与assembleRelease通过。Python未改，不重复先前92项。离线临时--dependency-verification=off，校验元数据未改。读取结果不等于成品。
- 当前新增读取入口：DouyinShareVideo/Client、DouyinReadPreparation、DouyinCaptureContext/ReusePolicy、SignedMediaUrlExpiry、DouyinPlaybackUrl；已有DouyinProbeActivity、发现/解析/会话入口同步。NativeHeader只走现有平台作用域，无新常驻按钮。
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
- 原AVD进程已退出后保数据重开，当前AVD PID31099，数据目录 `<private-evidence>/nanfeng-web-parity.KpOWcx/avd`，1920×1920/420dpi；AVD和正常MediaStore成品保留。收束时活动任务0，最终任务/取消态和旧MP3/DASH URI保留。
- 旧本任务HTTP服务器PID43595/8765、44099/8766、52428/8767目前均不存活；本轮未重启或关闭其他项目服务。复用须先核实原fixture目录并另建本任务服务。8767慢样本约每8192B延迟3秒，新合成20秒停止夹具在 `/slow/stop-long/release-hls.m3u8`，文件位于fixture根的 `stop-long/`。
- 本轮用于真实媒体回放的8个临时host清单/符号链接已精确移除；原媒体证据、AVD成品和原有夹具不删。UI XML及读取脚本在私有证据目录，不能整块输出含其他作品标题的XML；后续始终用最新语义bounds。
- Wi-Fi/移动数据和自动恢复未改，原网络等待/自动恢复开关验证沿用既有通过记录。进程重开通过，不声称AVD设备重启或OEM通过。

## 下一步安全入口

原四项与当前两条读取一秒样本已通过；当前优先正常UI恢复指定抖音任务并验证真实成品，不重下、清除或复写原樱花证据，不重复已通过流程。抖音第一任务73bfd20b-6ee9-47a1-bc35-964389b13021为PAUSED/0B/retry3，第二e2614ee5-c722-4428-9a5c-19efae738122为PAUSED/0B/retry0；保留任务，后续只正常UI恢复。正式递增版本、OPPO覆盖和GitHub发布须另获授权。

- `docs/verification/2026-10-10-douyin-first-read-under-one-second.md`：当前读取速度、风险回归、包和未验收边界。
- `docs/verification/2026-10-09-sakura-remux-identity-and-stop-closure.md`：本轮根因、红绿回归、完整哈希、正常UI与成品/取消证据。
- `docs/verification/2026-10-09-website-parity-completion-audit.md`：最新A–D完成矩阵和未验收边界。
- `docs/verification/2026-10-09-network-and-long-hls-recovery.md`：此前网络验证及本轮续传追补。
- `docs/verification/2026-10-09-pornhub-download-playback-and-navigation.md`、`2026-10-08-website-parity-code-and-emulator.md`：已通过样本历史记录；包哈希属于各自阶段。
- 方案 `docs/superpowers/plans/2026-09-27-android-desktop-website-parity-plan.md`；稳定架构 `docs/南枫下载 Android 完整开发档案.md`。

下一条只读命令：`git rev-parse --short HEAD`、`git status --short | wc -l`，以及显式 `adb -s emulator-5580 shell pm path com.nanzhufeng.videodownloader`。先按根AGENTS和专项Skill收窄验证，不推送/发布/操作主设备。
