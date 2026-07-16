# Android Room 与三页壳层验证结果

日期：2026-07-16  
分支：`feature/android-room-shell`

## 结论

Room 状态层、Repository、DataStore 和首页/历史/设置三页壳层已经实现。外屏已在 OPPO Find N5 上覆盖安装并真实启动；内屏物理屏当时处于关闭状态，因此只完成 2248 × 2480 同尺寸模拟验证，不能写成真机展开验证。

本阶段没有把正式智能读取和下载 Worker 接入 Room 队列。主页“智能读取”仍将当前输入原样传给既有技术探针，这是有意保留的阶段边界。

## 已实现

- Room version 1：媒体、活动任务、下载历史及稳定重复识别字段。
- `DownloadRepository`：入队、选择、合法状态转换和终态归档的唯一公开入口。
- Preferences DataStore：默认分辨率与输入草稿。
- 三个一级页面：首页、历史、设置。
- 首页直接观察活动任务，历史页直接观察归档记录，设置页直接观察持久化设置。
- 外屏底部导航；600dp 及以上窗口使用左侧导航栏。
- 内屏首页左右双栏，输入区位于总进度下方，只有一个“智能读取”按钮。
- 设置页保留既有 YouTube、抖音和 TikTok 技术验证入口。
- 任务状态同时使用文字和颜色表达，未仅依赖颜色。

## 自动化验证

- Python：9 项通过。
- Kotlin/JVM：25 项通过，0 失败，0 错误。
- Android 模拟器：Room、Repository、DataStore 重建读取、三页导航、输入传递、MediaStore、Media3、Python/yt-dlp 与 TikTok 探针共 15 项通过。
- 干净构建：`clean testDebugUnitTest assembleDebug assembleDebugAndroidTest` 通过，并生成应用 APK 与测试 APK。

## Find N5 真实验证

- 设备：OPPO Find N5，序列号 `3B157F009E800000`。
- 外屏：系统报告 1140 × 2616；使用覆盖安装，没有卸载或清除数据。
- 应用可启动，首页多行输入、总进度、队列空状态和底部导航均无重叠或截断。
- 真机外屏截图：[room-shell-find-n5-outer.png](../assets/android/room-shell-find-n5-outer.png)
- 系统同时识别内屏 2248 × 2480，但验证时内屏状态为 `OFF`，未进行物理展开切换测试。

## 内屏模拟验证

- 模拟窗口：2248 × 2480，密度 520dpi，对应约 692dp 宽。
- 实际采用 600dp 自适应边界，能够显示左侧导航栏与首页双栏；840dp 会在 Find N5 内屏误判为外屏布局。
- 关键操作位于左右内容区，没有放在中央折痕附近。
- 同尺寸模拟截图：[room-shell-find-n5-inner-simulated.png](../assets/android/room-shell-find-n5-inner-simulated.png)

## 待验证与未实现

- Find N5 物理展开、折叠过程中页面和输入草稿是否连续保持。
- 正式智能读取到 Room 队列、单任务下载和终态归档的完整链路。
- WorkManager/前台服务、后台下载、网络恢复自动续传和通知控制。
- 登录态、账号权限内容、自定义目录和下载后文件操作。
- 当前真实下载探针通过不等于正式队列机制已经完成。
