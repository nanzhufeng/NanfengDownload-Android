# 抖音首次智能读取一秒目标与风险修复

检查点：2026-10-10 09:54（Asia/Shanghai）。仅 emulator-5580；保留所有既有 WIP、任务、历史、原樱花缓存与成品。没有主设备操作、connected 测试、数据库注入、卸载、清数据、提交或发布。

## 已通过

正常 UI 输入并点击智能读取，设备/主机时钟偏移已校准。两条在当前进程首次解析目标短链，近期作品结果已过期；日志是原生 HTTP 捕获而非结果复用。首页完成 Repository 入队/重复检查、isReading=false 后记录终点，不以捕获回调、ID、入队或传输字节替代完整下载。

| 最终包样本 | 点击到真实来源捕获 | 点击到首页结果提交 |
| --- | ---: | ---: |
| 车子定损，7684884186910329202 | 0.584 秒 | 0.729 秒 |
| 果蝇神经元，7690158572814077184 | 0.563 秒 | 0.702 秒 |

最终包 SHA-256 `aac978994f128f8e8293e7aad02c577a239c09ea5895f1b5a1ec2f8c0edbfca6`；AVD base.apk 回拉相同。1.2.97/10297、Room 10，证书 c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。只作隔离验收包，未递增正式版本或安装主设备。

果蝇快速点击不在输入后等待 uiautomator 完整采样；只等待 450ms 键盘关闭动画，使用已观测的填写后按钮位置，结果返回后核对输入精确相等。前面的三次快捷脚本因旧坐标/IME动画没有触发 Activity，其中一次还与后续输入重叠，全部排除，不计应用读取失败或性能样本。最终有效操作串行完成，UI按钮恢复可用且显示未重复添加。

前后 DB 27任务/22历史、活动0；两目标各一条 PAUSED/0B，无新目标成品。旧MP3 URI114/17390B、DASH URI115/160020B、樱花 URI125/162297814B 保留。原四项 A–D媒体闭环证据沿用，不重复已经通过的工作。

## 根因和实现

1. 旧路径等待完整网页脚本/播放器；只开 HTTP 缓存不能消除首次加载。旧桌面原型 app/douyin.py 先 HTTP 再浏览器，本轮只读比较，未改旧原型，未声称测过用户当前桌面正式程序。轻量分享页+结构化探测先降为5.065/3.205秒，仍不达标。
2. 带现有访客会话的 H5 HTML 已声明目标视频，原生 DouyinShareVideo 解析 window._ROUTER_DATA，仅接受 exact aweme_id/awemeId 与目标相同的 video.play_addr.url_list。512KB、2000节点、12层上限；可信HTTPS、媒体路径和已知签名有效期门禁。只返回ID、错误/空正文、其他作品都不能早报成功。
3. 原生首轮冷进程仍3.367/3.273秒。分段测得点击到Activity1.431秒、UA准备0.074秒、HTTP请求1.763秒；预热前后对比支持首次连接准备为主要剩余延迟，但没有分别量出DNS/TLS。MainActivity打开后后台预热两个官方域名HEAD，输入有效链接时按60秒节流补充；无Cookie、无作品ID、无媒体/任务预取。客户端初始化在IO线程；这段有限连接准备独立于输入变更取消，实际读取Call仍可取消。
4. 元数据直接 HTTP 获取，6秒同作品网页回退及30秒错误收束保留。HTTP失败/没有声明来源会继续正常网页读取，不能把成功响应或短链身份当作媒体来源。
5. 新路径最初仅取IES域Cookie遗漏主站会话：主机空Profile响应仅ID，使用现有主站访客标识则约0.431秒返回目标详情。SessionSite补齐已列在cookieScopeUrls/isTrustedLoginUrl的IES官方域；原生请求通过SessionProvider.cookieHeaderFor复用既有平台Cookie合并，不写Netscape Cookie文件。只有受信任HTTPS/default端口/无userinfo可返回头，YouTube/其他站点/假域名/明文均无头；请求地址固定官方IES且不跟重定向。
6. 视频使用实际引擎浏览器UA，捕获保存该UA给下载解析；图集保持完整结构化/精确数量/原图模板契约。仅官方playwm地址、单一合法video_id转换为同ID的play地址，保留全部原查询编码；主机HEAD可访问且长度与playwm不同，但没有逐像素水印证明或最终成品证明。

## 风险修复与验证

- 视频缓存不能绕过note完整图集；旧记录缺UA/会话上下文、账户变化、过期/未来时间、已知签名过期强制重新读取。
- 登录指纹使用稳定session标识；sid_guard时间/TTL续期不再把同账户误判为换号，真实登录变更仍阻止旧源复用。
- URL作品ID只从官方完整路由提取；媒体判断基于path，拒绝query假mp4、凭据、异常端口和图片伪视频。
- 主页面跳转限制官方HTTPS/同作品；JS实际location参与视频/图集捕获，防SPA旧页面原子值误绑定。作者列表解析失败不得转为单作品推荐抓取。
- native成功同时更新CaptureStore和持久捕获；未初始化WebView销毁已保护。异步元数据回调检查销毁/finish；实际Call取消。正常UI在native request开始后按返回，取消提示通过，2.2秒后无晚到成功/新任务（证据包0ca7cb48阶段，相关取消逻辑在最终包保持）。
- 下载解析器兜底也校验作品、当前登录、来源和有效期；CancellationException不能吞掉变成成功。对应2项回归去掉防护时2/2失败，恢复后通过。首轮4项风险回归4/4红、会话续期1/1红、IES作用域1/1红均有保留记录。
- 最终Debug/Release JVM各340项，0failure/0error；JS4项。lint0Error/35Warning/3Information；Release/R8 Parcelable CREATOR门禁和构建通过。命令：./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleRelease --offline --dependency-verification=off --console=plain。未修改依赖验证元数据；本轮Python未改，先前92项不重复运行。JS夹具定位改为相对__dirname，仓库根运行也通过。

## 失败与待验证边界

旧11–14秒、早期原生冷连接3秒样本未达标，已定位并由最终正常UI样本替代；不能把0.09秒缓存复读当首次达标。正常网络、已有可用访客/登录会话、当前两条样本达标，不宣称任意网络/风控/全新空访客会话均稳定小于1秒。空Profile主机控制仍无视频详情，浏览器回退可能超时；OPPO实测、全新作品新增Room记录与更多平台/图集的一秒指标未逐个验收。

两条抖音真实下载、完整音视频解码、历史播放和重开仍待正常UI完成。本轮没有恢复下载，旧CDN TLS EOF只是旧来源的历史快照，新官方播放入口不能据HEAD当作成品完成。原樱花、通用身份、传输中停止闭环不外推成全部站点或主设备通过。

私有证据：/tmp/nanfeng-sakura-evidence-20261009/（700），含测速JSONL、红绿/构建日志、只读DB/Cookie副本、回拉APK。Cookie/临时源/媒体不提交。程序、AVD数据和原媒体证据保留。
