# 2026-10-09：公开 Pornhub 单作品下载、AAC 封装与底部导航

## 范围与结论

- 本记录只覆盖一个公开、无需账号即可由桌面同版 yt-dlp 解析的 Pornhub 单作品样本（`viewkey=648719015`）和两种模拟器视口的底部主导航。未代用户完成任何年龄确认，也未绕过登录、DRM 或付费限制。
- 隔离 AVD `NanfengWebParityTmp` 的最终签名 Release 经正常首页“智能读取 → 入队 → 下载 → 历史播放”再次完成本样本：Room `COMPLETED`，MediaStore 成品 67,927,311 字节，H.264 1280×720／AAC，时长约 360.94 秒；FFmpeg 全片音视频解码无错误，历史内置播放器的 SurfaceView 持续提交视频帧。此前的 17,302,421 字节、426×240 样本是第一次 360p 验证，不是最终包的文件大小。未在 OPPO 上覆盖安装或复验，不代表 Pornhub 所有作品或全部电脑版网站均可下载。
- 底部主导航在同一个独立悬浮组件中按视口收紧：外屏项目高度由 60dp 降至 52dp，内屏降至 56dp；外屏横向留白为 32dp，内屏胶囊最大宽度由 800dp 降至 720dp；底部额外间距由 12dp 降至外屏 4dp／内屏 6dp。继续保留 `navigationBarsPadding()`，不占系统手势区。模拟器分别以 1080×1920 和 1920×1920、420dpi 的设置页检查了导航尺寸、整块选中态与可点击性。

## 故障定位与修复

1. 先前 Android 元数据发现返回 410，网页后备停在年龄页，因此曾把站点年龄确认误述为下载前置条件。复核同一公开地址：桌面固定 yt-dlp `2026.8.19` 可直接提取；隔离 AVD 的 Java HttpURLConnection、OkHttp 和 Python 标准库请求均得到 200，只有嵌入 yt-dlp 的 UrllibRH 得到 410。问题在传输兼容性，不是已证实的年龄门禁。
2. `pornhub_http.py` 只对官方 HTTPS `*.pornhub.com/view_video.php?viewkey=` 启用兼容提取请求，并仅在该来源的 HTTPS `phncdn.com` 媒体传输中启用相同兼容请求；其他平台继续原 yt-dlp 通道。重试前，元数据可入队而分片传输仍报 410；补齐分片通道后，原失败任务在正常 UI 重试完成。
3. 首个下载成品虽含完整 H.264/AAC，却在隔离 AVD 内置播放器停在 0 秒 BUFFERING。对同一文件仅重封装后，原播放器能够播放；视频包未改变，原 MP4 的 AAC 包内仍有 ADTS 头。`AndroidStreamRemuxer` 原样把 ADTS AAC 写入 MP4；现在通过 `AacAdtsSampleLayout` 验证并剥去每帧 ADTS 头，同一个提取器采样内含多帧时逐帧写入并按 AAC 采样率推进时间戳；原始 AAC 与其他轨道保持不变。最终 720p 成品由 FFprobe 回读、FFmpeg 全片解码，内置播放器持续提交帧。多帧布局由 JVM 测试覆盖，未把该单一样本当成所有 ADTS 变体的证明。临时播放诊断代码未保留在产品源码中。
4. 导航收紧只改共享组件的几何与上下留白；三页路由、图标、标签“首页／历史／设置”和选中语义未删减，项目高度仍不少于 52dp。

## 验证分层

| 层级 | 本次结果 |
| --- | --- |
| Python | `unittest discover` 77 项通过；包括官方来源与 CDN 作用域、410 兼容和非目标网站保持原行为。 |
| Android JVM | Debug、Release 各 300 项通过；导航契约先红后绿，新增 AAC/ADTS 布局测试。 |
| 静态与构建 | `lintDebug` 0 error、35 warnings；`assembleRelease`、R8 Parcelable Creator 保留门禁、正式证书校验通过。既有本地 Gradle 依赖元数据哈希不匹配，本次只离线使用缓存并临时 `--dependency-verification=off`，未修改验证元数据。 |
| 隔离 AVD 下载 | 最终包正常 UI 再次处理同一公开作品，Room `PORNHUB / 648719015 / COMPLETED`，MediaStore `content://media/external/video/media/1000000113`，67,927,311 字节，`fileExists=1`。本次默认清晰度为 720p；先前 360p 成品为 `content://media/external/video/media/1000000077`、17,302,421 字节。 |
| 成品与播放 | 最终成品 FFprobe：H.264 1280×720、10,817 帧；AAC 16,919 帧；360.94 秒。FFmpeg `-xerror` 全片视频与音频解码 exit 0，无报错。正常历史缩略图打开内置播放器，SurfaceView 视频层累计有效帧 127，连续两次采样的末帧时间戳前进。强制停止 App 后重新启动，历史缩略图仍能再次打开并持续提交帧；这不是设备重启验收。未把“存在文件”单独当成可播放证明。 |
| 导航 | 同一正式包在 1080×1920 外屏模拟视口与 1920×1920 内屏模拟视口检查设置页；外屏另看首页。导航底边仍位于系统手势区上方。 |
| 主设备与发布 | 未触碰 OPPO，未运行 `connected*AndroidTest`，未推送 GitHub 或发布；当前仍为 `1.2.97 / 10297`，不能作为主设备递增版本覆盖。 |

当前 APK：`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.97.apk`，SHA-256 `05370785bb611667489b72f3ddd6560121141f42d0cd0bcb1a695291422751e8`，签名证书 SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。该哈希仅对应本地本次构建，不是 GitHub 交付物。

未确认：作品标题的人工逐字对应、OPPO 内外屏视觉与手势、该作品在 OPPO 的最终播放和重启回读、隔离 AVD 的设备级重启、其他 Pornhub 作品、樱花动漫最终文件以及更多电脑版站点样本。单一样本成功不能外推整个平台。本轮创建的本地媒体拷贝、UI XML 和模拟器 App 私有诊断拷贝已按精确路径清理；隔离 AVD 的正常 MediaStore 成品作为验收样本保留，未触碰用户主设备媒体。
