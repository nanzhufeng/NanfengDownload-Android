# 南枫下载 Android 当前交接

> 合同层级：历史交接。原文保留当时目标/状态，不代表全部目标已完成；现行实现及未达目标以[合同总入口](../contracts/README.md)为准，不从本页旧参数重建功能。


更新时间：2026-10-10（Asia/Shanghai）。当前任务：全平台下载稳定性及速度，普通目标至少1 MB/s、长片5–10 MB/s以上；公网长片目标尚未通过。此前119交接全文保存在`docs/archive/PROJECT_HANDOFF_BEFORE_20261010_DOWNLOAD_SPEED_STABILITY.md`。

## 当前源码与隔离正式包

Checkout `.`；HEAD d9a66a1/main；1.2.127/10327、Room10。保留全部原WIP及旧桌面原型，未改entity/schema/migration，无暂存/提交/推送/发布。

APK`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.127.apk`，57,620,500 B；SHA-256 7cec8a4cb9078934a4f299b82e3a9e8c967dd3172374810d4228dbcc5f38e2c0；证书c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。私有`<private-evidence>/nanfeng-transfer-stability-20261010/frozen-127.apk`与回拉installed-127.apk相等，5580同签名保数据覆盖，appId10210/首次安装时间保持。

JVM Debug/Release各409项、Python117项、lint0E/34W/3I、Release/R8/Parcelable通过；Debug追加重跑覆盖最终时钟回归，Python沿用最终125未再改源码。JBR/离线dependency-verification=off，依赖验证元数据未改；JS未改不冒称新跑。

## 已修复与已通过

HTTP瞬时错误有限重试和Retry-After冷却、前缀保全、并行有界分块、音视频按需并发分配、HLS/DASH最多6连接顺序组装、失败线程错误关闭共享输出、过期源错误并发覆盖已修。Python用256KiB读取、缓存桥接方法、4Hz网络通知但不缓存取消状态；分离纯传输/时间线/封装计时，封装进入真实MERGING避免假网络停滞。独立音视频使用已有Media3无损合并，唯一临时文件、实际双轨样本/无编码器门禁、失败取消保来源与旧缓存。

指定cn作品119真实完成URI172/138,221,078 B，全片音视频解码，122冷历史播放通过。指定YouTube DtzlRef7EsU在124正常UI正确目标重试，源403后自动重解析，URI178/648,056,409 B，全片解码、126冷历史播放通过；实际网络合计约4.79 MB/s，旧音视频合并174.927秒。受控HLS125 URI179/35,381,941 B全片解码、126冷历史播放通过，纯片段1.549秒/23.43 MB/s，封装5.166秒；属于本地夹具，且提升同时包含代理清理、缓冲及通知变化。

正常UI传输中停止584f0035…CANCELLED/无URI；126新合并中停止434be4a3…两源647,971,060 B已完整收到后MERGING时取消，CANCELLED/无URI/0成品，来源缓存保留。构建、100%字节和合并未完成均不算业务完成。

126快速合并89.489秒、188,034视频包/161,961音频包及解码通过，但音频累计偏移1.492秒，URI180明确不计合格。根因Media3 1.7.1音频固定48k时基逐样本取整；127按绝对PTS对齐48k/90k时基，回归累计漂移归零。127新受控DASH109115da…正常UI使用保全原公开视频拆分输入轨道，读取0.532秒、网络3.853秒、合并95.512秒（较原174.927秒约少45%），URI181/650,828,978 B。双轨包数相等、音频时长差约8微秒、逐包音频PTS及全片解码exit0/0B日志、冷历史播放127帧/2.517秒通过。公网URI178不变，此受控验证不算公网传输提速到5–10 MB/s。

## 保全与审计例外

原6e808840…失败字节169,610,956、原分片169,753,472、URI120缺帧证据保留，不因旧DB显示COMPLETED计通过；最终樱花ac701443…URI125/162,297,814 B全片解码和冷播放沿用旧阶段。MP3 URI114/DASH115身份路径/端口、五平台旧成品、灵动岛109字体/生命周期和短媒体URI169/170验收保留，不重复原已通过工作。

最终审计45任务/42历史：原36任务全部存在，仅预期cn任务完成字段和误点旧失败夹具2872babc…改变；原32历史仅该旧失败夹具completedAt/errorSummary改变。2872误点两次重试，retryCount0→2，仍FAILED、旧字节/URI/大小保持；已披露并改为标题/唯一卡片定位，绝不DB注入还原。其余旧任务/历史逐列相同，原URI178等不变。

119所谓代理恢复仅检查legacy字段，遗留本任务canonical host/port，旧直连/代理标签不能证明路由因果。本轮按精确匹配清除该临时残留，最终五字段均未设；自建fixture端口61490、精确PID测试服务、对应adb reverse及本任务UI XML已清理，失败缓存/成品保留，最终无活跃下载及.mux.part。

## 边界与下一入口

只操作emulator-5580/NanfengWebParityTmp；无OPPO/其他模拟器/connected Android测试/数据库注入/卸载/清数据/缓存清理。旧主设备覆盖授权已履行，本轮不复用部署主设备。所有私有证据`<private-evidence>/nanfeng-transfer-stability-20261010/`；动态完整记录`docs/verification/2026-10-10-download-speed-stability-second-audit.md`；A–D审计继续按样本区分。

127本轮实现/正式包/正常UI/全片解码及清理闭环已完成，工作流审计HARD_ERRORS none。下一安全入口先核对127/frozen/installed哈希与本报告，避免重复原已通过工作；公网长视频持续5–10 MB/s仍待真实网络验证，所有平台每次首次读取≤1秒仍沿用119未通过边界。更多作品/其余樱花集/B站多段durl/复杂PAC或SOCKS/OEM/设备重启/TalkBack没有新增通过证据。
