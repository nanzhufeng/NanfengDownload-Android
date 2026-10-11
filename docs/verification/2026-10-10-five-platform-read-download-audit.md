# 五平台读取与真实下载审查（2026-10-10）

范围仅 Android 源码及 emulator-5580 / NanfengWebParityTmp。根 AGENTS、项目排错/开发/验证/审查/文档 Skill 和当前交接已读取；不操作 OPPO、5592，不运行 connected*AndroidTest，不注入数据库，不卸载或清数据，不提交、推送或发布。

## 当前结果与证据边界

| 平台 | 样本 | 读取 | 真实成品、解码、历史 |
|---|---|---|---|
| 抖音 | 7684884186910329202（车子定损）、7690158572814077184（果蝇） | 最终1.2.100正常UI点击到结果提交0.954/0.739秒；捕获0.756/0.653秒，均capture，无近期结果复用 | 同一原任务正常UI恢复，输出153/154，完整音视频解码通过；最终包正常历史播放与冷进程重开持续提交帧 |
| YouTube | aqz-KE-bpKQ，公开 Big Buck Bunny | 正常UI智能读取入队 | 真传输、分离音视频合并输出157，完整解码、正常历史与冷重开播放通过 |
| TikTok | @scout2015/video/6718335390845095173 | 原Android提取失败；新原生目标SSR读取通过 | 首轮0B/403无成品；补齐媒体Cookie后同任务正常UI重试，输出160，完整解码、冷重开历史播放通过 |
| Pornhub | viewkey=648719015 | 本轮正常UI重读，未重复入队 | 沿用2026-10-09的67,927,311B真实成品、全片解码/播放/重开证据，不重复已通过下载 |
| 哔哩哔哩 | BV1bK411W797 | App网络失败，主机与5580均无法解析www/api主机；m主机另见TLS中断 | 未入队、未产生新成品，真实下载待网络恢复后验证；不能列为通过 |

抖音与YouTube本轮成品是在1.2.97中真实传输，最终1.2.100验证重新读取/旧URI/历史冷重开；TikTok真实重试传输在1.2.100。以上是指定样本结果，不是所有作品、账号、网络或主设备均通过。Pornhub cn独立Android下载、每平台更多作品、TikTok短链实站和哔站bangumi等仍未扩大验收。当前没有主设备覆盖或远端授权。

## 已确认问题与修复

1. TikTok移动端SSR使用 `webapp.reflow.video.detail`，现有yt-dlp读取 `webapp.video-detail`，Android报Unexpected response；标准页面同目标结构已只读保全。`TikTokPageVideo`使用原生OkHttp、官方HTTPS作品页、不跟重定向、8秒总上限/2MiB正文上限；只解析两个明确目标字段，statusCode必须0、item ID必须相等，媒体须可信HTTPS/默认端口且未过期。Native字段解析失败仍交给已有提取器，无推荐作品/封面替代，无新常驻控件。
2. 首轮原生元数据有效，真实传输仍403。主机提取结果存在媒体Cookie；无Cookie范围请求403，原提取器媒体Cookie请求206。原生页面响应Cookie现在经Cookie.parseAll、expiresAt与目标URL匹配筛选，作为videoCookieHeader进入已有Resolver/DirectMediaTransfer；媒体凭据只发往提取到的精确主机，跳转跨主机仍去除。普通headers不含Cookie。修改后5580同任务从0B失败变为真实2,953,029B完成，是设备端因果闭环。未扩散账号材料/创建Cookie文件/持久化新会话字段。
3. 分离音轨的格式请求头曾覆盖视频请求头，可能使视频用错UA/Referer。现在分别合并顶层+视频/音频提示，分别去除凭据；audio_headers复用已有Kotlin字段，不改Room。YouTube正常单视频实际走默认提取器，之前看到的来源识别兼容配置缺口不构成本次样本失败根因，未强制更换YouTube客户端。
4. 哔站不存在的分P曾静默选第一P，现在失败拒绝替代；BV/av请求与页面声明身份必须匹配，BV有效载荷区分大小写。多个顺序durl旧片段曾被当画质候选，现无有效DASH时拒绝只下载其中一段。旧多段格式完整封装仍未实现，此门禁防止假完成，不计其下载支持已实现。
5. TikTok已解析短链只确认官方作品身份，避免在来源识别阶段重复提取媒体。针对短链的隔离回归通过，但没有把它称为短链实站下载已验收。

初始三个Python风险回归3/3失败；短链重复提取和多段durl两项追加回归均红；修复后Python98项通过。TikTok旧Android解析失败、1.2.98目标字段未匹配、1.2.99元数据有效但403分别保留阶段证据；最终1.2.100成功不抹去前述红灯。

## 真正的成品

| 作品/任务 | MediaStore URI尾号 | 成品大小B | 音视频/时长秒 | SHA-256 |
|---|---:|---:|---|---|
| 抖音车子，73bfd20b-6ee9-47a1-bc35-964389b13021 | 153 | 17,043,396 | H264 720×1280 / AAC44.1k双声道，149.141769 | ebf218bf2053035f59636f6ce9a192d075034c00741f38ab234020a2215511ef |
| 抖音果蝇，e2614ee5-c722-4428-9a5c-19efae738122 | 154 | 46,497,925 | H264 1280×720 / AAC44.1k双声道，368.547007 | 803bad750b91728fc99976c7f5fe5b34955505ffe54f309cbfce4cc886457ba1 |
| YouTube，bc680fd3-e204-42f5-abe5-fc9e486c89f8 | 157 | 161,025,060 | H264 1280×720 / AAC44.1k双声道，634.624580 | 726c447694cce29475d0caeaaa15b8103f9d847ee73d662563ca90d119770359 |
| TikTok，ee49cb1c-3a4c-4950-b0a9-fc6da827ce80 | 160 | 2,953,029 | H264 576×1024 / AAC44.1k双声道，10.495011 | d2899f4428fa4c46a8dca6b0222bc313ffdeff0fd8ed6f99a300f1833b3fda3e |

所有四份新成品均从真实MediaStore URI查询并拉取，`ffmpeg -v error -xerror -i … -map 0:v -map 0:a -f null -`全片exit0，错误输出0B。最终包正常历史播放，包覆盖后冷进程重开（不清数据、不重启AVD）：TikTok127帧/4.234秒、YouTube126帧/2.083秒、果蝇127帧/4.200秒、车子127帧/4.201秒；来自播放器SurfaceView BLAST的有效present时间，不以截图/控件出现代替播放。

## 包、自动验证与保全

最终版本1.2.100/10300、Room10。无entity/schema/migration变化。签名证书SHA-256 c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547；最终APK及5580回拉base SHA-256均9062c9c7de9eb5b1a8193cbc3fda7af7e807a941be10562da51eb8e8c743eb27。产物 `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.100.apk`；仅5580同签名递增版本pm install -r --user 0，firstInstallTime仍2026-10-08 19:24:25。

- Android Studio JBR运行 `:app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleRelease --offline --dependency-verification=off --console=plain`：Debug/Release各347项、lint0Error/35Warning/3Information、Release/R8/Parcelable CREATOR门禁通过；依赖校验元数据未改。
- 同源Python3.14/yt-dlp2026.8.19：`PYTHONPATH=android/app/src/main/python …/python -m unittest discover -s android/app/src/test/python`，98项通过。JS未改，沿用此前4项通过。
- 接手122个WIP路径，不暂存、提交、恢复或删除用户源码/证据。最终29任务/26历史；无PARSING/DOWNLOADING/PROCESSING/VALIDATING，旧未选通用夹具642efae5…仍WAITING，不等同活动下载。失败/取消旧项的状态、retryCount与字节和接手快照相等。启动按钮实际只处理待下载/暂停项，TikTok最终以自己的正常UI重试按钮完成。
- 旧MP3 URI114/17,390B、DASH URI115/160,020B、樱花最终URI125/162,297,814B保留；两条抖音重读没有新增任务或改变153/154成品URI。原6e808840…失败缓存/源清单和最终樱花完整解码、取消门禁不重跑、不销毁。
- 私有证据仍 `<private-evidence>/nanfeng-sakura-evidence-20261009/`（700）：five-platform-db、各final.mp4/ffprobe/decode日志、各冷播放latency、read-benchmarks.jsonl、final build日志和回拉base。Cookie/临时媒体URL/页面响应只在私有目录，不复制进仓库。仅移除本轮自己的APK安装暂存及UI dump。

## 待验证

哔站现网读取与真实下载仍红；需恢复www.bilibili.com/api.bilibili.com的解析及TLS后，在5580正常UI重读同BV并完成传输/合并/全片解码/历史重开。没有修改系统代理/DNS、猜测或固定CDN地址。旧多段durl仅拒绝不完整输出，完整旧格式支持另需协议级实现与成品闭环。更多平台作品、受账号/地区/会员限制的来源、空访客会话和主设备用户路径均未逐项验收。

原A–D已通过部分见2026-10-09-sakura-remux-identity-and-stop-closure.md与website-parity-completion-audit.md；本记录只补五平台审查与四份真实成品，不把它外推成全部网站/作品验收。

## 2026-10-10 下载性能与生命周期追补

新增哔站BV1bK411W797_p1实站读取、音视频独立真实传输/合并、全片解码和历史/冷重开通过；此前DNS失败保留为当时证据。新增抖音真实14,700,391 B/6.189秒、单连接/零重试及首字节前正常UI取消无URI。最终1.2.104冻结正式包，Debug/Release各361项、lint/R8通过；真实传输在1.2.103，最终1.2.104同URI冷历史播放，不能混称全部五平台由最终版本重下。原29任务/26历史逐列不变，原樱花失败证据和URI114/115/125/153/154/157/160保留。主设备仍1.2.100，本轮只读，未覆盖或操作其任务；读取有1.316秒超标，不承诺每次1秒。详见 `2026-10-10-download-performance-and-lifecycle.md` 和当前根交接。
