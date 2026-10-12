# 南枫下载 Android 当前交接

> 合同层级：历史交接。原文保留当时目标/状态，不代表全部目标已完成；现行实现及未达目标以[合同总入口](../contracts/README.md)为准，不从本页旧参数重建功能。


更新时间：2026-10-10 15:58（Asia/Shanghai）。唯一当前交接；本轮前109全文归档`docs/archive/PROJECT_HANDOFF_BEFORE_20261010_READ_PERFORMANCE.md`。当前任务：指定cn.pornhub作品慢读取与全部平台读取优化，严格每次1秒仍未全部通过。

## 当前结果与边界

官方轻量元数据路径、Generic同请求快照、后台导入与连接准备已实现；修复Android正则兼容、哔站原生API412试验、Python系统HTTP代理路由与Java桥接兼容，哔站读取减少至一次view API，下载时仍完整解析并拒绝试看/DRM。读取、入队、传输、完整媒体和发布分层，未将0B任务算下载完成。详见`docs/verification/2026-10-10-cross-platform-read-performance.md`。

仅emulator-5580/NanfengWebParityTmp；无OPPO/5592/其他设备操作，无connected Android测试、数据库注入、卸载、清数据、应用缓存清理、暂存/提交/推送/发布；全部既有WIP和旧桌面原型保留。系统代理只作临时对比，已精确恢复原null。旧主设备覆盖授权已履行，不复用它部署新包。

## 当前源码、正式包与门禁

- Checkout `.`；HEAD d9a66a1/main；1.2.119/10319、Room10，未改entity/schema/migration。
- APK`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.119.apk`，57,587,732B；SHA-256 ce606dcea1a58db5bf6c4e66734cbab928ba732f5ab3b6168f5307889b571ad0；证书c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。
- 私有`<private-evidence>/nanfeng-read-speed-20261010/frozen-119.apk`与回拉installed-119.apk和正式包哈希相等。仅5580同签名递增保数据覆盖，appId10210/首次安装时间保持；不在重构后沿用此哈希。
- 最终JVM Debug/Release各395项、Python115项通过；lint0E/35W/3I，Release/R8/Parcelable通过。JBR、离线dependency-verification=off，未改验证元数据。JS未改，旧4项不冒称本轮新跑。
- 最终36任务/32历史；原35/32及本轮109首次读取后的36/32均逐列相等。新cn作品b05e4973-421c-42e2-a275-2eba13205532仍WAITING/0B，无新增成品；原未选WAITING642efae5…selected0、旧URI/字节/失败/取消均保持，没有活跃传输。

## 本轮实际成功、失败及限制

109指定cn网址14.757–17.116秒间完成，主机同版完整提取6次请求7.373秒。Android右花括号未转义导致快速页面始终回退，已实机诊断修正。原生哔站API实际412，试验已移除。117代理桥接21ms失败不计提速，118集合/枚举兼容后正常读取恢复。

119：指定cn直连1.067/1.940/0.310/0.309秒，临时代理0.326秒；恢复原网络冷进程1.567秒也成功，六次中位0.697、最慢1.940。YouTube直连1.436/代理1.540；哔站0.702/0.723；TikTok1.535/1.148；抖音原短链0.618/0.882秒。全部成功且旧作品未重复添加。API payload4341/859/约1750B；不能以元数据字节当下载视频。API可选作者ID/上传日期缺失不造值；Pornhub用平台名，publish_date与网页uploadDate含义不同，旧记录未更新。没有跨读取结果缓存或自动入队预取。

## 既有成品、UI与A–D保全

原灵动岛109字体/生命周期/动画、短媒体29,596B URI169与35,351B URI170完整解码/历史冷播放，沿用`2026-10-10-island-feedback-and-short-media.md`及UI契约；未重跑或改动UI。原下载性能与生命周期修复、抖音真实传输、哔站独立音视频合并，沿用`2026-10-10-download-performance-and-lifecycle.md`。

旧五平台代表成品URI153/154/162/167、YouTube157、TikTok160、Pornhub648719015、哔站165按原版本证据保留。樱花6e808840…原169,610,956B失败及169,753,472B原分片、URI120缺帧记录不清；最终ac701443…URI125/162,297,814B全片解码/历史/冷重开沿用旧闭环。旧MP3 URI114/DASH115、通用身份路径/端口和停止取消验收保留，见A–D审计。

## 待验证与下一安全入口

严格所有平台每次首次≤1秒没有通过，现有超标/网络失败已记录，不用最快值掩盖；更多作品、私密账号、小红书/未知站点、119真实下载/解码/历史播放、复杂PAC/多代理/SOCKS与主设备业务未逐项验收。原B站多段durl完整支持、其余樱花集、设备级重启、TalkBack等旧边界保留。

下一步先读本交接及性能报告，核对5580正式包与frozen-119，不重复已通过下载、不删失败证据、不操作主设备。继续针对实际网络超标分阶段定位，不能因一次构建、安装、API或入队宣布全部完成。
