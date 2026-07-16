# 南烛枫视频下载器 Android 可行性验证结果

日期：2026-07-16

工程：`D:\CodexProjects\CleanVideoDownloader-AndroidProbe`

分支：`feature/android-find-n5-probe`

目标设备：OPPO Find N5，型号 `PKH120`，Android 16

## 1. 结论

Android 本地下载技术路线可行。YouTube、抖音和 TikTok 均已走通至少一个真实公开视频的本地解析、下载、媒体校验与系统公共媒体库写入链路。

TikTok 公开单视频已在 Find N5 上真实通过。TikTok 作者目录的分类、分页、作者隔离和去重已实现；桌面环境首批 50 条通过，Find N5 两批各 5 条通过，但短时间连续目录请求出现明显限流，50 条真机规模未完成，因此当前结论仍是“可行性验证工程”，不是可发布的完整 Android 应用。

## 2. 已实现

- 抖音、YouTube、TikTok 链接分类，单视频与作者/频道入口相互隔离。
- TikTok `www.tiktok.com/@作者/video/作品ID` 单视频解析。
- TikTok `www.tiktok.com/@作者` 作者目录按每批 50 条读取和继续加载。
- TikTok `vm.tiktok.com`、`vt.tiktok.com` 分享短链接延迟分类。
- 作者作品按作品 ID 去重，并依据频道 ID、上传者 ID、公开账号名和规范 URL 剔除其他作者。
- 单视频渐进式 MP4，或分离视频与音频的下载、Media3 合并和媒体容器校验。
- 网络中断有限重试、`.part` 断点续传和下载取消。
- 写入 Android 公共 `Movies/南烛枫视频下载器/Probe/`。
- TikTok 媒体请求携带 yt-dlp 会话 Cookie；Cookie 仅在内存中传递给对应媒体请求，不记录、不落盘。

下载范围保持为公开内容；不实现会员、付费、DRM、私密内容或区域限制绕过。

## 3. 自动验证

### Python

- 命令：`python -m unittest discover -s android/app/src/test/python -p 'test_*.py'`
- 结果：9 个测试通过。
- 覆盖：媒体流选择、竖屏短边分辨率、TikTok 渐进式 MP4、Cookie 请求头、作者身份隔离、去重和分页游标。

### Kotlin / Android 构建

- `testDebugUnitTest`：22 个测试通过，0 失败，0 跳过。
- `assembleDebug`：通过。
- `assembleDebugAndroidTest`：通过。
- 应用 APK：`android/app/build/outputs/apk/debug/app-debug.apk`，50,618,743 字节。
- 测试 APK：`android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，972,003 字节。

## 4. TikTok 补充验证

测试样例来自 TikTok 官方开发者文档：

- 单视频：`https://www.tiktok.com/@scout2015/video/6718335390845095173`
- 作者主页：`https://www.tiktok.com/@scout2015`

### 4.1 单视频

- 初始失败：元数据解析成功，但 Android 直链下载返回 `HTTP 403`。
- 根因：yt-dlp 会话生成了 `ttwid`、`tt_csrf_token`、`tt_chain_token`，原桥接层只返回普通请求头，没有把目标媒体 Cookie 传给 Kotlin 下载器。
- 修复后桌面最小验证：`HTTP 200`，读取 4096 字节，`Content-Type: video/mp4`。
- Find N5 仪器测试：1 个测试通过，耗时 3.885 秒。
- 真机断言：平台为 TikTok；下载文件大于 0；媒体容器校验通过；MediaStore 返回 `content://` URI。

状态：**已在 Find N5 真实验证**。

边界：测试运行器没有保留本轮文件精确字节数与 URI 文本；手机随后断开调试连接，未再次使用系统播放器人工打开该文件。因此“系统播放器画面与声音”仍是待验证项。

### 4.2 作者目录

- 桌面真实主页：首批 50 条约 6.4 秒，重复 0，其他作者 0，存在下一页，下一起点 51。
- Find N5：第一页 5 条约 3.511 秒。
- Find N5：第二页 5 条约 175.774 秒；两页合并 10 条、其他作者 0、作者标识唯一，测试通过。
- Find N5：随后首批 50 条在 300 秒内未完成；此时设备已经历多次连续主页请求，符合平台限流或移动网络重试特征。
- 实现没有 500 条总量上限；列表通过“加载更多”继续按批读取。

状态：**功能已实现；桌面 50 条已真实验证；Find N5 两批 5 条已真实验证；Find N5 50 条规模待验证**。

完整应用阶段建议加入目录页缓存、请求冷却和可见的延迟重试，不应通过无限重试隐藏平台限流。

## 5. 既有平台真机证据

### YouTube

- 合并 MP4：29,939,539 字节。
- MediaStore URI：`content://media/external/video/media/1000153362`。
- 轨道：H.264 1280x720 + AAC。
- SHA-256：`2E4884A22F17257B248C15EA33F97C9BCBB5DA3904EDAFC90096121976D6C2B5`。

### 抖音

- 作品 ID：`7659318944100076838`。
- MP4：99,788,011 字节。
- MediaStore URI：`content://media/external/video/media/1000153370`。
- 轨道：H.264 720x1280 + AAC。
- SHA-256：`9FABCDEBE7975E2C171ED5692844CC0FD1B2A208F823E58F6A7EEAB43F48F4A8`。

## 6. 待验证风险

- TikTok 作者目录在 Find N5 上的 50 条及更大规模稳定性。
- TikTok 连续分页的缓存和限流恢复策略。
- TikTok 下载结果使用系统播放器人工检查画面与声音。
- 抖音软件内真实账号登录及重启后的账号状态恢复；目前只验证过 Cookie 数据目录可跨强制停止保留。
- 完整 Android 应用的主页、下载历史、设置页、后台任务、系统通知和折叠屏适配仍未实现。

## 7. 停止条件与下一步

本轮停止在“可行性验证成立、风险已记录”的边界，不继续扩展完整 Android 应用。

下一步应先实现下载任务与历史数据模型，再搭建主页、历史、设置三个正式界面；TikTok 作者目录需要在正式批量下载前补充分页缓存和冷却重试设计。
