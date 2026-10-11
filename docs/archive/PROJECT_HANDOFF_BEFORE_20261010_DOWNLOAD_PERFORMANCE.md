# 南枫下载 Android 当前交接

更新时间：2026-10-10 10:38（Asia/Shanghai）。本文件为当前唯一交接正文；五平台审查前09:54正文完整保留在 `docs/archive/PROJECT_HANDOFF_BEFORE_20261010_FIVE_PLATFORM_AUDIT.md`。此前樱花、网址身份、取消闭环不重复，按下列证据继续。

## 当前结论

用户随后明确要求“覆盖安装”：OPPO主设备已按正式同签名pm install -r --user 0由1.2.97/10297升级至1.2.100/10300；最终回拉base哈希与冻结APK9062c9c7…43eb27相等，数据目录CE/DE inode、UID、首装时间和15条媒体记录指纹不变。没有自动启动或主设备业务操作，没有整库/账号内容读取。详情 `docs/verification/2026-10-10-oppo-preserving-overlay.md`。此前下文“仅5580/主设备未操作”只描述五平台审查阶段。最新授权已完成，仅限本次覆盖，不授权后续主设备下载/测试、远端推送或发布。

最新用户要求特别检查抖音、YouTube、Pornhub、哔站、TikTok的读取和下载并修复。仅操作emulator-5580 / NanfengWebParityTmp；本轮四份真实成品均全片音视频-xerror解码通过，最终包历史与冷进程重开持续提交帧。哔站实站读取因当前DNS/TLS仍失败，不能报五平台全部通过。主报告 `docs/verification/2026-10-10-five-platform-read-download-audit.md`。

- 抖音原两任务恢复完成：73bfd20b… URI153/17,043,396B；e2614ee5… URI154/46,497,925B。最终1.2.100正常UI重新HTTP读取车子0.954秒、果蝇0.739秒（捕获0.756/0.653），均capture、无近期结果复用；重复检查不新增记录/不改成品URI。此前0.729/0.702仅是此前阶段实测，不能替代当前值。
- YouTube aqz-KE-bpKQ，任务bc680fd3-e204-42f5-abe5-fc9e486c89f8，真传输/音视频合并完成URI157/161,025,060B，634.624580秒，H264 1280×720/AAC44.1k双声道。普通单视频默认提取器实际通过，未强制改客户端。
- TikTok @scout2015/video/6718335390845095173，原Android解析报Unexpected response；移动SSR实际使用webapp.reflow.video.detail。原生目标解析补齐后先0B/403拒绝发布；再补页面响应Cookie的媒体域/路径/有效期匹配，同任务ee49cb1c-3a4c-4950-b0a9-fc6da827ce80正常UI重试在1.2.100完成URI160/2,953,029B，H264 576×1024/AAC双声道，10.495011秒，全片解码/冷历史播放通过。
- Pornhub viewkey648719015本轮正常UI重读未重复入队，沿用10-09的67,927,311B全片解码/播放/重开，不重复下载；cn独立Android下载未验收。
- 哔站BV1bK411W797正常UI网络失败：主机getaddrinfo与5580ping均unknown host www/API；m域另见TLS中断。未入队/无新成品，需网络恢复后继续实站。已修复不存在分P替代第一P、BV/av页面身份匹配（BV大小写敏感）、多段durl当画质导致只选一段；多段旧格式当前仅拒绝假完成，完整支持未实现。

抖音/YouTube成品由本轮1.2.97真实传输，最终1.2.100做重读/旧URI/冷历史重开；TikTok由最终1.2.100真实传输。仅上述样本，不外推所有作品、账号、网络或OPPO。更多作品、TikTok短链实站、空访客会话、B站bangumi、设备级重启与主设备用户路径未逐一验证。

## 当前源码、包和验证

- 路径 `.`，HEAD d9a66a1/main。接手122个WIP路径保留，无暂存/提交/推送/发布；新增原生TikTok、回归与审查记录。
- 最终1.2.100/10300、Room10，无entity/schema/migration变化。APK `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.100.apk`；APK与5580回拉base SHA-256均9062c9c7de9eb5b1a8193cbc3fda7af7e807a941be10562da51eb8e8c743eb27，证书c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。仅5580同签名递增保数据覆盖，firstInstallTime仍2026-10-08 19:24:25。
- Debug/Release JVM各347项、同源Python98项通过；lint0Error/35Warning/3Information、Release/R8/Parcelable CREATOR门禁和assembleRelease通过。JS未改，沿用此前4项。Android Studio JBR；离线临时dependency-verification=off，校验元数据未改。
- 新增TikTokPageVideo，经官方HTTPS作品页、不跟重定向、8秒/2MiB上限，明确desktop/reflow目标字段、status/作品ID/可信新鲜媒体校验；媒体Cookie按响应作用域匹配后进入已有DirectMediaTransfer精确主机门禁。取消检查、失败回退维持原链路；不记录Cookie值，不新增Cookie文件/控件。
- youtube_probe.py音视频headers分别保留各自格式提示并去除凭据；audio_headers复用既有Kotlin模型。TikTok短链只解析身份避免双提取；B站分P/作品身份/旧多段拒绝门禁已回归。抖音原生秒读与图库完整门禁未改。
- 最终29任务/26历史，无解析/下载/处理/验证活动；旧未选通用夹具642efae5…仍WAITING，不等同活动下载。旧失败/取消项状态、retryCount、字节与接手快照相等，未恢复其他任务。最终回到首页；本轮APK暂存与UI dump精确清理，AVD及成品保留。

## 原A–D与证据保全

- 原任务6e808840-ec08-4e6d-bcc1-91953dfc078f失败缓存/源清单、169,753,472B原segments及其哈希不动。URI120虽中间完成但边界缺帧，不计最终验收。
- 樱花最终ac701443-dfc4-4abd-a4c8-ee9318b3a462 URI125/162,297,814B，1229.886秒/H2641920×810/AAC44.1k双声道，全片解码、AAC负载比对、历史和冷重开通过，沿用 `docs/verification/2026-10-09-sakura-remux-identity-and-stop-closure.md`。原12集发现与选择、跨路径/端口身份门禁及正常UI传输中停止CANCELLED无URI均沿用通过证据，不重跑。
- 旧MP3 URI114/17,390B、DASH URI115/160,020B、樱花URI125保留；原樱花真实源与最终媒体、失败/取消证据均保留在 `<private-evidence>/nanfeng-sakura-evidence-20261009/`（700）。新增四份final.mp4、完整ffprobe/decode/冷播放latency、read-benchmarks、five-platform-db与包回拉均私有；原始页面/临时媒体URL/会话材料不进入仓库。
- AVD5580当前仍PID31099，数据 `<private-evidence>/nanfeng-web-parity.KpOWcx/avd`；OPPO与5592未操作。禁止connected*AndroidTest、DB注入、卸载/清数据。旧8765/8766/8767服务均未重启，本轮无新host服务。本次主设备正式覆盖已依最新授权完成；无远端授权。

## 下一步安全入口

优先恢复/核实本任务网络对www.bilibili.com与api.bilibili.com的DNS/TLS，再仅5580正常UI重读BV1bK411W797并验证真实下载/合并/完整解码/历史重开。不猜测固定CDN地址，不修改系统代理/DNS，不以单测代替实站。其后扩展每平台不同作品与TikTok短链；原已通过四份成品、樱花/取消/身份不重下或销毁证据。

- 当前五平台：`docs/verification/2026-10-10-five-platform-read-download-audit.md`。
- 此前抖音秒读与风险：`docs/verification/2026-10-10-douyin-first-read-under-one-second.md`。
- 原四项：`docs/verification/2026-10-09-sakura-remux-identity-and-stop-closure.md`、`2026-10-09-website-parity-completion-audit.md`。
- Pornhub：`docs/verification/2026-10-09-pornhub-download-playback-and-navigation.md`。
- 方案：`docs/superpowers/plans/2026-09-27-android-desktop-website-parity-plan.md`；稳定架构：`docs/南枫下载 Android 完整开发档案.md`。

下一条只读命令：显式 `adb -s emulator-5580 shell pm path com.nanzhufeng.videodownloader` 与5580 DNS检查，先确认当前设备/源码/网络，再按项目Skill继续。无推送/发布授权；本次主设备覆盖授权已履行，不扩展为主设备业务操作。
