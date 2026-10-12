# 南枫下载 Android 当前交接

> 合同层级：历史交接。原文保留当时目标/状态，不代表全部目标已完成；现行实现及未达目标以[合同总入口](../contracts/README.md)为准，不从本页旧参数重建功能。


更新时间：2026-10-10 12:05（Asia/Shanghai）。唯一当前交接；本轮之前正文完整保留在 `docs/archive/PROJECT_HANDOFF_BEFORE_20261010_DOWNLOAD_PERFORMANCE.md`。用户继续要求下载卡顿/慢速全面检查和尽可能优化；原樱花、身份、取消闭环不重复下载或销毁证据。

## 当前结论与边界

下载性能及生命周期修复已完成源码、自动门禁与隔离正常UI代表性闭环：抖音完整单连接；空多连接失败降级并保留连续断点；探测固定实际CDN；响应头/正文阻塞可及时取消；首个进度/总量立即报告；速度基准和缓存速度排除；0字节不预显示5%；不再因系统联网验证波动反复取消；主页仅检查启动时真实Room快照，KEEP已有Worker，避免手动开始被当成中断恢复。详情 `docs/verification/2026-10-10-download-performance-and-lifecycle.md`。

OPPO仍1.2.100/10300：此前“覆盖安装”已完成，该授权的执行证据见 `2026-10-10-oppo-preserving-overlay.md`，本轮没有自动部署新包或操作其下载任务。当前仅只读logcat/UI/包信息；临时UI dump已精确删除。未经新的明确授权，不覆盖主设备、不推送或发布；主设备真实下载速度尚未验收。

## 当前源码、冻结包和验证

- 路径 `.`；HEAD d9a66a1/main。全部既有WIP保留，无暂存/提交/推送/发布；仅Android链路与证据文档，旧桌面原型未改。
- 最终1.2.104/10304、Room10，未改entity/schema/migration。APK `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.104.apk`，46,236,679 B；SHA-256 e5f30cb58778228f037cbc97e80201ff18a783eb7cb1b49d2e59cf174e32c8c7；证书c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。冻结副本、5580回拉base与此APK相等；不要再重构冻结包后沿用旧哈希。
- Debug/Release JVM各361项通过，lint0Error/34Warning/3Information、正式Release/R8/Parcelable CREATOR门禁通过。Android Studio JBR，离线dependency-verification=off，验证元数据未改。Python/JS本轮未改，沿用此前98/4项，未重跑。
- 仅5580 / NanfengWebParityTmp做递增同签名保数据覆盖；appId10210/firstInstallTime2026-10-08 19:24:25保留。没有connected*AndroidTest、DB注入、卸载/清数据、其他模拟器操作。
- 接手29任务/26历史，最终33/30；逐列比较原29条任务与26条历史全部相等，旧失败/取消状态、retryCount、字节、URI不变。无PARSING/DOWNLOADING/VALIDATING活动；旧未选通用夹具642efae5…仍WAITING/selected0。最终回首页，暂存APK与UI dump已精确清理，AVD及证据保留。

## 新增实站、停止与速度证据

- 主机同一份17,043,396 B抖音来源，真实HttpFileDownloader三连接/完整单连接/1 MiB小分块耗时2.831/1.062/13.495秒，SHA全部相等。单次顺序对照有预热与网络变化影响，不是OPPO速度或固定倍率保证；临时联网JUnit已移出仓库。
- 新抖音7684629008277036371，6294f2cd-8782-4d61-9e28-cfa77f21d9ab，1.2.101先复现系统验证取消，恢复后单连接完成URI162/7,449,365 B，68.266667秒/H264720×1280/AAC，全片音视频解码退出0、stderr0 B。
- 正常UI停止7678601534321478948任务990e7e66…：1.2.102手动开始→DOWNLOADING 0 B→本卡停止→CANCELLED，无成品URI/fileSize0，最终取消证据保留。本次停止发生在首字节前，收到字节后的取消沿用原10-09通过证据，不混称此次重验。
- 同作品再经正常智能读取形成02ca59d2-2870-429b-86f4-e07e4c2c5c66：1.2.103单连接14,700,391 B/6.189秒，零重试，URI167，44.768005秒/H264720×1280/AAC44.1k双声道，全片解码退出0/0 B日志；原取消任务保留。
- 哔站网络现在恢复：正常UI读取BV1bK411W797_p1并入队3e6cb15f-9750-4287-887e-b8f6ec06463f；1.2.103视频流5,851,248 B/3.073秒、音频流2,026,507 B/2.558秒并行，各单连接、无重试，实际合并URI165/7,880,923 B。HEVC852×480/AAC44.1k双声道/90.302200秒，720p为请求上限，实际可用480p；全片音视频解码退出0/0 B日志。
- 抖音/哔站正常历史播放及冷进程重开通过；最终1.2.104生产修复相同、只增版本，另验同URI冷历史：抖音25帧/1.020秒、哔站27帧/1.387秒。不是设备级重启，也不是1.2.104重传全部媒体。
- 新作品正常UI HTTP读取0.599/0.871秒，后一次重读1.316秒；无近期缓存复用。“每次严格1秒内”仍有失败，不用最快值覆盖超标。此前原车子/果蝇1.2.100实测0.954/0.739秒沿用。

## 五平台与原A–D保全

五平台现在均有代表性真实下载证据：抖音原两样本URI153/154、新样本URI162/167；YouTube aqz-KE-bpKQ URI157/161,025,060 B；TikTok @scout2015/video/6718335390845095173 URI160/2,953,029 B；Pornhub既有648719015/67,927,311 B；哔站新增URI165。各样本完整解码/历史重开按原报告与本轮报告分版本记录；YouTube/TikTok/Pornhub没有为了刷结果重下。不能外推所有作品、地区、会话或所有版本五平台重新传输。

- 旧抖音原两任务73bfd20b…/e2614ee5…原URI153/154保持；YouTubebc680fd3…、TikTokee49cb1c…和Pornhub真实通过沿用 `2026-10-10-five-platform-read-download-audit.md` 及其引用。旧报告的B站DNS失败为当时事实，当前新增读取/下载通过以本轮报告为准。
- B站不存在分P/作品身份/旧多段拒绝、音视频独立headers、TikTok移动SSR/响应Cookie精确作用域、抖音图集完整门禁等已有修复保留。B站旧多段durl完整顺序支持未实现；TikTok短链、空访客会话、Pornhub cn独立Android、更多作品/其余樱花剧集、设备级重启及OPPO业务路径仍未逐项验证。
- 原6e808840-ec08-4e6d-bcc1-91953dfc078f失败缓存/源清单与169,753,472 B原segments不清理；URI120缺帧不计最终验收。樱花最终ac701443… URI125/162,297,814 B、1229.886秒/H2641920×810/AAC，全片解码、AAC负载对照、历史/冷重开沿用 `2026-10-09-sakura-remux-identity-and-stop-closure.md`。
- 原MP3 URI114/17,390 B、DASH URI115/160,020 B及不同路径/端口身份门禁保持。原证据 `<private-evidence>/nanfeng-sakura-evidence-20261009/`；本轮私有包、快照、媒体、耗时/解码/播放证据 `<private-evidence>/nanfeng-douyin-transfer-20261010/`，700目录/600文件。临时CDN/会话不入仓库。

## 下一步安全入口

当前交付包已可审阅；主设备最新覆盖需明确授权并重读当时数据/签名指纹，保数据覆盖不等于主设备业务验收。不得自动操作OPPO任务。其后仅5580扩展不同作品、TikTok短链/空会话、B站多段及超1秒读取的分阶段耗时；不要重下已通过样本或删除失败证据。

- 本轮：`docs/verification/2026-10-10-download-performance-and-lifecycle.md`。
- 五平台旧阶段：`docs/verification/2026-10-10-five-platform-read-download-audit.md`；原A–D：`2026-10-09-website-parity-completion-audit.md`及樱花报告。
- 方案：`docs/superpowers/plans/2026-09-27-android-desktop-website-parity-plan.md`；稳定架构：`docs/南枫下载 Android 完整开发档案.md`。

下一条只读：显式 `adb -s emulator-5580 shell pm path com.nanzhufeng.videodownloader` 并核对冻结1.2.104哈希；再按项目Skill进入授权范围。保留全部WIP；不操作5592或主设备任务，不推送、不发布。
