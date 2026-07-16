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

## 当前验证证据

- JVM：`testDebugUnitTest` 通过，共 68 项测试、0 失败。
- 静态检查：`lintDebug` 通过，无阻断问题。
- 构建：`testDebugUnitTest + lintDebug + assembleDebug` 于本轮全新执行，`BUILD SUCCESSFUL in 1m 8s`。
- 模拟器全套仪器测试：`NanzhufengFindN5Api35`、Android 15/API 35、arm64，XML 记录 28 项、0 失败、3 项跳过；跳过项均为需要外部实时 TikTok 条件的 `TikTokLiveProbeInstrumentedTest`。
- MP3 定向验证：2 项 LAME PCM 编码、2 项 M4A→MP3/取消清理、1 项 `DirectMediaTransfer` 主链路端到端测试均通过；转码测试连续运行两次通过。
- 模拟器 UI：冷启动、通知权限、首页、历史、设置均可到达；应用进程定向错误日志为 0 行。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`，约 59 MB，SHA-256 `78ace7ce4e4613d2ad64e3fa7f0f0af1229bdf2f4708625d8b7e0134868db9f0`。
- APK 完整性：`unzip -t` 返回无错误；两套 ABI 均包含 LAME 与 JNI 共享库。
- OPPO 连接状态：设备序列号 `3B157F009E800000`、型号 `PKH120` 已授权 ADB；截至本记录仍未安装本轮 APK、未卸载应用、未清空数据。

## 尚未完成，禁止宣称正式版落地

1. 在 OPPO 上记录已有安装/版本状态，并使用 `adb install -r` 做不清数据覆盖安装。
2. 在 OPPO arm64 上运行 LAME、M4A→MP3、DirectMediaTransfer 定向测试及完整仪器测试。
3. Find N5 外屏、内屏切换和任务状态连续性真机验收。
4. YouTube、抖音、TikTok 的公开单视频，以及作者/频道/播放列表真实读取、过滤、分页与下载；需要登录/cookie/地区网络的路径必须单独标明外部条件。
5. 取消勾选不下载、有效已有文件跳过、断网恢复、用户暂停不自动恢复、完成通知、历史归档和重启后状态保留的真机生命周期验收。
6. 正式版本号、Release APK/AAB、签名与覆盖升级策略；当前产物仍是 `0.1.0-probe` Debug APK。

## 下一阶段唯一任务

先在 OPPO 上无损覆盖安装本轮 Debug APK，完成 arm64 真 MP3和全套仪器验证，再进入三平台公开内容与任务生命周期的用户路径验收。遇到签名不兼容必须停止，禁止通过卸载旧应用绕过。

执行依据：

- `docs/superpowers/specs/2026-07-16-android-true-mp3-encoding-design.md`
- `docs/superpowers/plans/2026-07-16-android-true-mp3-encoding-plan.md`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DirectMediaTransfer.kt`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3AudioTranscoder.kt`
