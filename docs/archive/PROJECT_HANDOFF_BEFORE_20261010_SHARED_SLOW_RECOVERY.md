# 南枫下载 Android 当前交接

> 合同层级：历史交接。原文保留当时目标/状态，不代表全部目标已完成；现行实现及未达目标以[合同总入口](../contracts/README.md)为准，不从本页旧参数重建功能。


更新时间：2026-10-10（Asia/Shanghai）。当前增量：按用户灵动岛建议和最新南枫转写0.14.7实现后台下载进度、简洁/详细模式与稳定性。此前127完整交接归档`docs/archive/PROJECT_HANDOFF_BEFORE_20261010_BACKGROUND_ISLAND.md`。

## 当前正式包与范围

Checkout `.`，HEAD d9a66a1/main；1.2.130/10330，Room10。WorkManager2.9.1→2.10.5以取得SDK35前台dataSync超时停止修复，原队列/任务/缓存/发布事实链不变，不改entity/schema/migration或媒体传输代码。

APK`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.130.apk`，57,653,479 B；SHA-256 a12a37198d79917b7ad67c2eaff187429d74d160aec160b35dfc9c27dc3a5848；证书c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。仅5580从127→128→129→130同签名保数据覆盖，appId10210/首次安装时间保持；`<private-evidence>/nanfeng-background-island-20261010/frozen-130.apk`与installed-130.apk及正式包相等。

开发验收只操作emulator-5580/NanfengWebParityTmp；无转写器5592或其他模拟器操作，无connected Android/仪器测试、Debug设备部署、数据库注入、卸载、清数据、应用缓存清理、暂存/提交/推送/发布。保留全部原WIP和旧桌面原型。后台灵动岛开发增量未操作OPPO；随后用户再次要求覆盖安装并明确指定OPPO，已仅做正式包保数据更新，见下文。

2026-10-10后续OPPO覆盖：1.2.100/10300→1.2.130/10330，原包/新包证书一致；使用冻结正式包`pm install -r --user 0`返回Success，回拉base与上述正式包SHA-256一致。UID、dataDir、首次安装时间、用户0的ceDataInode/deDataInode保持；15条本App发布媒体的ID/大小/修改时间指纹相等。安装前后无下载前台服务，未自动启动或操作任务；私有Room未读取，不把目录身份保留称为任务全表验证。远端本次临时APK已删除。记录`docs/verification/2026-10-10-oppo-release-130-overlay.md`，私有旧包/冻结包/回拉包/证据`<private-evidence>/nanfeng-oppo-overlay-130-20261010/`。OPPO真实后台下载、灵动岛模式和OEM长期行为仍待用户路径验证。

## 已实现、自动与隔离验收

同一DownloadIslandState驱动前台通知与悬浮窗，真实阶段、阶段百分比、本任务速度和下载量；未知总量不造百分比，处理/保存未结束最多99%，完成只取真实校验发布归档结果。进度1Hz，任务/阶段/终态及时更新；窗口/通知更新失败不取消传输，WorkManager管理已有前台唤醒锁与恢复，不建第二下载服务。

默认一行简洁模式（可见34dp/触控48dp/至少220dp宽/左右24dp留白），点击展开详细，点击标题收起；进度保留用户模式，新任务/返回App后再后台重置简洁。详细提供标题/质量/字节/剩余、查看下载与停止。ActualActivity回调判断本App可见，权限撤销、锁屏、屏幕非交互隐藏，通知PRIVATE；Noto550/400与等宽数字沿用下载器。下载规则加入后台权限/通知/电池入口，关于功能审阅同步，无首页新常驻按钮；查看下载及进度通知真实导航到首页。

JVM Debug/Release各416项，零失败/错误/跳过；lint0E/34W/3I及Release/R8/Parcelable通过。JBR/离线dependency-verification=off，验证元数据未改。Python/JS本轮未变，旧117/4不冒称新跑。红绿覆盖保存阶段、借用别的任务速度；128实际熄屏窗口残留（无系统锁屏）已由129的PowerManager.isInteractive判断修复。

正常UI受控下载3个完整成品URI182/183/184各35,381,941 B，与自主生成H.264/AAC24秒原片完整SHA相等，全片双轨解码exit0；129冷历史打开URI182，127帧/5.253秒推进。夹具主动限速以留出操作时间，不用于公网速度基准。

129任务708f6606…：默认简洁→详细→更新仍详细→点击收起，熄屏窗口消失且下载925,696→2,760,704 B，唤醒简洁；返回App隐藏、重新后台简洁；原设置页点击查看下载回首页。岛内停止17,833,984 B得到CANCELLED/空URI/0成品。128任务b6c29434…同样从17,702,912 B正常岛内取消、缓存保留，但不计128熄屏窗口隐藏通过。自动化点按曾在窗口刚建立时未展开，读取稳定实测边界重试成功，该脚本失败已记录。

最终50任务/47历史，本轮前45任务/42历史全部逐列相等；无活跃下载、下载FGS、灵动岛窗口或本App活动ProcessorForegroundLck。正常UI悬浮窗已授权，仅隔离模拟器。1920×1920/420dpi模式联系表`docs/qa/background-island-v129/modes-contact.jpg`是四状态对比，不是同一屏规格。

## 证据与旧结果保全

稳定合同`docs/background-download-island.md`；本轮记录`docs/verification/2026-10-10-background-download-island.md`；私有日志/快照/包/媒体/操作脚本`<private-evidence>/nanfeng-background-island-20261010/`。本轮61510服务器/对应reverse/UI XML已精确清理，失败缓存/取消部分和所有成品保留。

127此前下载修复与速度证据沿用`2026-10-10-download-speed-stability-second-audit.md`：指定长YouTube公网约4.79 MB/s仍未达5–10标准，合并174.927→95.512秒、音频累计取整修复及URI181闭环保留；本地HLS23.43 MB/s不能外推公网。119每次首次≤1秒仍未全部通过。旧误点2872重试差异是前轮历史，本轮该记录没有再改变。

原樱花6e169,610,956B封装错误/169,753,472B原分片/URI120缺帧证据及最终ac701443…URI125保留，MP3/DASH身份路径/端口、旧五平台成品、灵动岛109前台短反馈与短媒体URI169/170保留，不重跑原已通过样本。

130最后收口移除系统超时自动全队列暂停，避免改变未选中的WAITING和干扰后续恢复；平台停止由WorkManager2.10.5处理，原中断恢复/断点保留。129模式/熄屏/停止实测与130相同组件哈希、416/416门禁、正式覆盖及同URI冷播放分层，不称130重新传输了全部样本。工作流HARD_ERRORS none。

## 下一安全入口与待验证

先核对130/frozen/installed哈希及本轮报告，再进入用户明确的下一项。OPPO/OEM长期后台、真正6小时平台时限、Android16配额、复杂网络/强杀/关机恢复、放大字体/更多屏幕和厂商原生岛尚未新增验收；应用悬浮窗不等于OEM原生岛，不承诺无限后台。读取每次≤1秒、公网长片5–10 MB/s及其余樱花集/更多作品/B站多段durl仍沿用之前未通过边界。
