# 抖音视频探测网页环境修复与成品验收阻塞

时间：2026-10-09 23:55至2026-10-10 00:09（Asia/Shanghai）。HEAD d9a66a1/main；只操作emulator-5580，保留全部既有未提交改动。无connected测试、数据库注入、卸载、清数据、主设备操作或远端变更。

## 红灯、对比与修复

- 样本1：`https://v.douyin.com/-ZorUgKuyOo/`，作品7684884186910329202，车子定损。样本2：`https://v.douyin.com/rXi4JDPO_to/`，作品7690158572814077184，果蝇神经元。
- 两条均在原固定桌面Chrome138探测页正常UI复现30秒超时；目标ID正确，video只有未就绪blob，详情HTTP200空响应。AVD无登录会话不等于用户主设备没登录。
- 相同探测页切移动UA后，两条都返回目标标题和可信HTTPS视频源并完成捕获。仅切桌面UA引擎版本未恢复。此前记录只看移动页早期快照，漏掉随后23:51:54/23:52:53的样本1捕获，已更正；blob出现是初始化过程，不能立即判定最终读取失败。
- 根因边界：视频探测统一用了图集的桌面网页环境，与移动登录页不同；这两个样本在相同隔离环境中，移动页能够加载，桌面页不能。该证据不证明主设备版本与运行环境完全相同。
- 最小修复：`DouyinProbeActivity.browserUserAgent` 对视频使用去wv标识、保留实际引擎版本的移动UA；完整图集继续桌面UA，短链跳转note时的严格重载逻辑保留。没有改错误文案、放宽blob/HTTPS/目标作品门禁、接任意网络推荐流或修改Room。
- 回归先忠实保留旧桌面选择，18项定向测试中视频配置用例失败；修复后Debug/Release各313项全过。新增两项覆盖视频移动会话契约和图集桌面契约。

## 构建与正常UI绿灯

- 命令：Android Studio JBR下 `./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleRelease --offline --dependency-verification=off --console=plain`。BUILD SUCCESSFUL；lint零Error、35 Warning、3 Information，R8 CREATOR门禁通过。未改既有依赖校验元数据。
- 隔离包仍1.2.97/10297，SHA-256 `16ba43a82bc454a86d62d84778d1e4bd26bd7e09278b3e8ce059e3b5667a156e`，证书SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。仅5580同签名保数据覆盖，安装base.apk回拉哈希一致。不是递增版本主设备交付。
- 修复包从正常启动器打开：首页输入短链→智能读取。样本2于00:01:20、样本1于00:02:03安全捕获。没有调试器UA覆盖、Activity extras或数据库写入替代验收。
- 诊断对比阶段经正常UI回调已创建样本1任务73bfd20b-6ee9-47a1-bc35-964389b13021和样本2任务e2614ee5-c722-4428-9a5c-19efae738122；修复包重读没有重复入队。前后均27个队列记录，目标各一条；这些只是读取/入队证据。

## 失败/待验证：真实媒体成品

- 正常开始下载后，样本1长时间DOWNLOADING但0B、0总量；正常停止和再次开始后仍为0B。未取得应用最终错误详情，不能将0B自行解释为已失败或完成。
- 主机对捕获的同一v26-weba.douyinvod.com URL做HEAD，Python为SSLEOFError；curl HTTP/1.1和HTTP/2均exit35、HTTP000、SSL_ERROR_SYSCALL。样本2同域HEAD也TLS EOF。此为主机网络层证据，不能替代Android失败栈。未改主机代理/DNS、全局网络或证书校验，也未随意更换CDN来源。
- 正常UI暂停后最终样本1PAUSED/0B/retryCount3，样本2PAUSED/0B/retryCount0，活动任务0。目标没有download_history记录/输出URI；MediaStore无is_pending视频。没有取得任何本次成品，不声称真实下载、完整解码、历史播放或重开通过。样本2应用传输尚未独立验证，不能说它与样本1发生相同应用连接故障。
- 下一步优先取得应用传输失败类型/栈并检查该隔离网络到目标CDN的TLS路径；保留现有任务，正常UI恢复，不清缓存/重建数据。不把本轮读取绿灯或此前樱花绿灯当作这两条视频完成。
- 用户截图设备的关于版本及会话页可播放性仍待回复；无OPPO覆盖授权。本包不能自动覆盖用户主设备。

私有回滚和最小证据保留在 `<private-evidence>/nanfeng-sakura-evidence-20261009/`：改动前DouyinProbeActivity源码、原四项闭环APK、红/绿日志、安装包回拉、只读数据库副本及捕获元数据。含临时URL参数或其他作品数据的原文不提交。
