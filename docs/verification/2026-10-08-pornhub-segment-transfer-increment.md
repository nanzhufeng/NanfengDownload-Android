# Pornhub 接入与分段传输首批代码验证

日期：2026-10-08。范围：9 月 27 日网站范围对齐方案的 A 代码增量，以及 A 依赖的 HLS/DASH 传输基础。不是整份方案完成声明。

## 当前状态与保护范围

- 基于本地 HEAD `d9a66a1` 加现有未提交工作区继续开发；之前的抖音、设置等修改均保留。本轮未提交、推送、安装或发布。
- Gradle 版本仍为 `1.2.97 / 10297`，Room 仍为 schema 9。本轮没有修改版本号、签名配置、entity 或 schema，没有清理用户数据。
- 所有变化位于 Android 当前链路，未修改根目录旧桌面原型。

## 已实现

1. `UrlClassifier` 接受 `pornhub.com` 和其子域的 `/view_video.php?viewkey=…` 单作品链接；拒绝冒名域名、嵌入账号密码和缺失作品标识。
2. `Platform`、`DownloadPlatform`、发现映射、平台显示、历史筛选、输出目录与首页输入提示接入 Pornhub。使用原来的 Repository、Worker、MediaStore/SAF 和历史链路，没有第二套任务状态。
3. `web_media.py` 将新网站的选流协议与旧五平台选择合同隔离。Pornhub 校验提取 ID 与输入 viewkey；保存解析出的标题和原作品 URL。分段计划不作为普通文件 URL 下载；音轨和视频轨道各保留自己的 Referer/请求头，不携带其他平台会话。
4. `YtDlpMediaInfo → YtDlpTaskMediaResolver → ResolvedMedia → DirectDownloadRequest` 透传临时分段计划，未持久化 Cookie 或 CDN 签名清单。
5. `ProtocolFileDownloader` 对普通直链保持旧 `HttpFileDownloader`；标记的 HLS/DASH 使用 `segment_transfer.py` 和锁定版本 yt-dlp 原生分段下载。顺序分片、实际字节进度、缺片失败、暂停/取消和续传；分段计划改变时换缓存指纹，不混用旧分片。
6. `AndroidStreamRemuxer` 将 TS/fMP4 音视频轨道重新封装为独立 MP4 后再进入已有合并、MP3 转换、分段、校验与发布流程。原始分片文件不会直接以 MP4 成品交付。封装控制内存、拒绝加密轨道、归零时间戳，并在取消/失败时清理本次未完成封装。
7. 分段 HTTP 失败桥接为既有 `HttpDownloadException`；403 等地址失效沿原来的 `TransferReprobePolicy` 重新解析同作品。记录分段传输报告，网络字节通过真实响应读取累计、包含清单；峰值和内部重试次数未统计，在报告说明中明确，不可把 0 解释为实测零重试。
8. 修复本轮受控取消/404 测试暴露的 yt-dlp 输出流及错误响应关闭问题。最后一次 Python 全量运行未再输出 ResourceWarning。

## 红绿证据

- 原始 Kotlin 新测试：`UrlClassifierTest.pornhubWorkKeepsItsOwnPlatformAndSource` 因 `IllegalArgumentException` 失败，证明旧入口拒绝目标网站。
- 原始 Python 新测试：通过公开 `extract_single()` 输入只含 HLS 的合成作品，因“没有找到可下载且具备音频的 MP4 视频流”失败。
- 完整 Android 回归首次还发现首页文案合同测试固定为旧五平台提示；同步为新增 Pornhub 的准确文本后通过，未删除测试或跳过断言。

## 最终验证结果

| 验证 | 结果 | 能证明什么 |
| --- | --- | --- |
| Python 全量 unittest | 59 项，全部通过 | 原有解析回归及新网站元数据/身份/请求头合同 |
| 本地真实 HLS TS、HLS fMP4、DASH | 包含在上述 59 项内；真实 yt-dlp 下载，FFprobe 检查音视频轨道及字节 | 原生分片传输可工作；不是 Android 最终封装/真机播放证据 |
| 缺片、取消、取消后续传 | 包含在上述测试内，全部通过 | 缺片不能假成功，取消保留可恢复分片 |
| Debug JVM | 263 项，0 失败、0 错误、0 跳过 | 分类、发现、Resolver、下载桥接、过期重解析及旧功能回归 |
| Release JVM | 263 项，0 失败、0 错误、0 跳过 | Release 变体编译与同一组 JVM 合同 |
| `lintDebug` | 0 errors，43 warnings | 本轮没有清理无关 lint 警告 |
| `assembleDebug` | 通过 | Android 编译和开发包打包；没有安装设备 |
| `git diff --check -- android` | 通过 | 差异空白检查 |

复现命令（Python 需隔离环境安装 `android/app/src/main/python/requirements.txt` 的锁定依赖；本轮主机 Python 3.14，嵌入配置仍为 Python 3.13）：

```sh
PYTHONPATH=android/app/src/main/python <隔离Python> -W always::ResourceWarning -m unittest discover -s android/app/src/test/python
cd android
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

原始测试报告：`android/app/build/test-results/testDebugUnitTest/`、`testReleaseUnitTest/`；lint：`android/app/build/reports/lint-results-debug.txt`。它们会被后续构建更新。本轮只使用 FFmpeg 生成本地合成色块和音频夹具、FFprobe 检查结果；没有向 App 引入桌面 FFmpeg，也没有下载用户视频。

## 未达到的验收与后续

- Pornhub 真实网络、真实作品下载及手机端最终音画播放未执行。现有证据不能称“Pornhub 真机下载已解决”。
- Android `MediaExtractor/MediaMuxer` 对实际站点 TS/fMP4 编解码组合的兼容性、长视频内存/性能，以及 Release R8 运行时桥接仍待隔离环境与授权正式包验收。
- 通用网站的 Python 适配基础存在，但 Android 尚未开放 `GENERIC` 入口。任意网页、媒体直链、多链接、动态播放器、合集/剧集等 B/C/D 用户流程仍未完成。不得根据模块名或分段引擎宣称已与桌面范围一致。
- 不支持直播录制、DRM 绕过、BT/种子或磁力解析；未新增这些能力。
- `context_gate.py` 返回 HANDOFF（36 轮，context 92%）；按 `plan-vertical-software-increments`、`maintain-codex-workflow` 的分阶段规则，本次完成首批代码/回归后收口，不开启新的功能增量。下一轮先读当前交接和本记录，再完成 A 的 Android 最终文件验收、推进 B/C/D。
