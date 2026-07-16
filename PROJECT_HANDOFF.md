# 南烛枫视频下载器 Android 当前交接

更新时间：2026-07-16（Asia/Shanghai）  
当前分支：`codex/android-notification-history-checkpoint-20260716`

## 当前目标

把 Android 正式工作台完整落地到 OPPO Find N5，并以真实设备上的正式用户路径验收为结束条件。模拟器、构建或 APK 生成不能替代 OPPO 真机结论。

## 已完成阶段

### Mac 构建与原生工具链

- `android/gradlew` 已恢复可执行权限。
- Chaquopy 目标 Python 保持 3.13，构建解释器路径改为 `nanzhufeng.buildPython` Gradle 属性或 `NANZHUFENG_BUILD_PYTHON` 环境变量。
- 当前 Mac 使用 `/opt/homebrew/bin/python3.13`；`android/local.properties` 只保存本机 SDK 路径且不进入 Git。
- 原生工具链固定为 Android NDK `26.1.10909125`、CMake `3.22.1`，构建 ABI 为 `arm64-v8a` 和 `x86_64`。
- 用户级 Gradle 旧显式代理会与当前 Clash/TUN 链路产生 TLS 握手失败；原配置已备份为 `~/.gradle/gradle.properties.before-nanzhufeng-android-20260716.bak`。当前网络下首次拉取依赖统一使用 `--max-workers=1`。

### 真正 MP3 编码

- `AUDIO_MP3` 不再要求来源扩展名必须是 `.mp3`，也不再存在把 M4A/MP4 政名为 MP3 的路径。
- Android `MediaExtractor` / `MediaCodec` 把来源音频流式解码为 PCM 16-bit；LAME 4.0 JNI 以单声道 128 kbps CBR、双声道 192 kbps CBR、quality 2 编码 MPEG Layer III。
- 完成文件必须同时通过 ID3/帧同步头、`audio/mpeg` 轨道、正时长、采样率和声道数校验，才会返回给现有 MediaStore/Repository 流程。
- 中断或失败只删除任务缓存中的转码半成品，保留来源文件；现有目标文件不被静默覆盖。
- 已纳入官方 LAME 4.0 完整源码，归档 SHA-256 为 `3df5124d5ad3a98312ffd7ba6a9b36230e4f8a3e66d3ce0f425e336c32d216eb`；许可和来源说明在 `android/app/src/main/assets/licenses/lame-4.0/`。
- APK 内含独立的 `libmp3lame.so` 与 `libnanzhufeng_mp3.so`，arm64-v8a 和 x86_64 两套均已构建。

### 外屏历史筛选

- `HistoryFilters` 已从横向滚动行改为状态、平台、日期三个 `FlowRow`。
- 380dp Compose 仪器测试覆盖筛选容器、“全部”、“全部平台”和“近 30 天”可见性。
- 720×1280 模拟器视觉检查中，状态、平台、日期筛选均完整换行，无横向滚动。

### OPPO Find N5 真机落地

- Debug APK 已使用覆盖安装方式落到 OPPO `PKH120`，应用数据未清除：`firstInstallTime` 保持 `2026-07-16 18:55:48`，本轮 `lastUpdateTime` 为 `2026-07-16 19:12:22`。
- OPPO Android 16/API 36 arm64 上的完整仪器测试通过：31 次执行、0 失败；3 项仅因 TikTok 外部实时条件跳过。
- Find N5 外屏 1140×2616、展开内屏 2248×2480、折回外屏均已真机检查；进程和当前页面在折叠状态切换期间保持连续。
- Android 16 不允许音频写入 `Movies` 的真机问题已修复：视频继续保存到 `Movies/南烛枫视频下载器`，MP3 改存 `Music/南烛枫视频下载器`，并新增真实 MediaStore 仪器回归。
- YouTube 公开样本 `Me at the zoo` 已在 OPPO 上完成读取、下载、M4A→MP3 转码和系统媒体库发布；历史记录为“完成”，文件 448.2 KB。
- 从 OPPO 媒体库读回的成品经 `ffprobe` 确认为真实 MP3：19.121583 秒、192 kbps、44.1 kHz、双声道；重复提交相同链接与“仅音频”规格后进入“已跳过”，未重复下载。
- 强制停止并重新启动应用后，“完成”和“已跳过”两条历史仍在，证明结果持久化正常。
- TikTok 官方公开示例已真机成功读取标题、作者并进入队列；抖音公开链接已进入真实解析链路，但当前外部条件明确要求 fresh cookies，未伪报成功。

## 当前验证证据

### 三页折叠屏视觉优化（2026-07-16）

- 首页、历史、设置已统一为薄荷绿工作台：共享字阶、圆角、卡片语义色和明显的实心选中态均由 `core/ui` 统一拥有。
- 历史读取投影固定为完成记录；状态筛选行已移除，平台/时间筛选仍保留；完成卡的打开、复制、分享、删除均收纳到三点菜单。
- 设置已固定 YouTube 首位，抖音与 TikTok 连续位于“短视频平台”分组；内屏使用账号全宽、其余模块双列的自适应网格。
- 视觉回归的 JVM、lint 与 debug APK 构建门禁均通过：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
- OPPO Find N5 后台覆盖安装已改用 `adb push` 后的 `adb shell pm install -r --user 0`：`2026-07-16 22:57:13` 成功，无图形安装器确认页；不要再使用会触发 ColorOS 确认页的 `adb install`。
- OPPO 外屏 1140×2616 已独立捕获首页、历史、设置；内屏 2248×2480 已独立捕获同三页；展开后折回外屏仍停留在历史路由。截图均在 `.artifacts/ui-audit-2026-07-16/`，该目录不入 Git。
- 已在模拟器中覆盖历史筛选/更多菜单、内外屏导航、首页空状态和设置账号顺序/网格；OPPO 当前没有完成历史记录，因此真机未展示有内容的时间线卡和三点菜单；历史筛选条件与首页输入草稿的跨折叠连续性仍由自动化状态所有者保障，未在这次空数据真机中逐项人工填写验证。

- JVM：`testDebugUnitTest` 通过，共 68 项测试、0 失败。
- 静态检查：`lintDebug` 通过，无阻断问题。
- 构建：MediaStore 修复后重新执行 `testDebugUnitTest + lintDebug + assembleDebug`，`BUILD SUCCESSFUL in 30s`。
- 模拟器全套仪器测试：`NanzhufengFindN5Api35`、Android 15/API 35、arm64，XML 记录 28 项、0 失败、3 项跳过；跳过项均为需要外部实时 TikTok 条件的 `TikTokLiveProbeInstrumentedTest`。
- MP3 定向验证：2 项 LAME PCM 编码、2 项 M4A→MP3/取消清理、1 项 `DirectMediaTransfer` 主链路端到端测试均通过；转码测试连续运行两次通过。
- 模拟器 UI：冷启动、通知权限、首页、历史、设置均可到达；应用进程定向错误日志为 0 行。
- MediaStore 定向验证：新增 1 项真实 MP3 写入 `Music`、内容校验和清理测试，Android 15 模拟器通过。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`，约 59 MB，SHA-256 `5702a012ec4a4e635dbae0dba5ebcf77bd255cf8f21be5b6ebaf5f54e0c36ccc`。
- APK 完整性：`unzip -t` 返回无错误；两套 ABI 均包含 LAME 与 JNI 共享库。
- OPPO 安装状态：设备序列号 `3B157F009E800000`、型号 `PKH120`，包版本 `0.1.0-probe`/`versionCode 1`；通知权限已授予，应用可直接启动。

## 尚未完成，禁止宣称正式 Release

1. 抖音真实公开内容目前被平台 fresh-cookies 要求阻断；需要在设置中的登录会话完成一次抖音 Cookie 刷新后再验。
2. 作者/频道/播放列表的大批量分页、过滤与取消勾选仍需更长时间的公开内容回归。
3. 断网恢复、用户暂停不自动恢复和完成通知已有自动化覆盖，但尚未在本轮 OPPO 用户界面逐项人工触发。
4. 正式版本号、Release APK/AAB、发布签名与后续覆盖升级策略仍未配置；当前安装的是可用的 `0.1.0-probe` Debug 验收版。

## 下一阶段唯一任务

如需对外分发，下一阶段只做 Release 收口：确定正式版本号和签名保管方案，构建 Release APK/AAB，并在不清数据前提下验证 Debug 验收版到正式签名版本的升级边界。抖音 Cookie 属于外部登录条件，单独处理。

执行依据：

- `docs/superpowers/specs/2026-07-16-android-true-mp3-encoding-design.md`
- `docs/superpowers/plans/2026-07-16-android-true-mp3-encoding-plan.md`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DirectMediaTransfer.kt`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3AudioTranscoder.kt`
