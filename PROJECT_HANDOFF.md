# 南烛枫视频下载器 Android 当前交接

更新时间：2026-07-16（Asia/Shanghai）  
当前分支：`codex/android-notification-history-checkpoint-20260716`

## 当前目标

把 Android 正式工作台完整落地到 OPPO Find N5，并以真实设备上的正式用户路径验收为结束条件。模拟器、构建或 APK 生成不能替代 OPPO 真机结论。

## 已完成阶段

### Mac 构建适配

- `android/gradlew` 已恢复可执行权限。
- Chaquopy 目标 Python 保持 3.13，构建解释器路径改为 `nanzhufeng.buildPython` Gradle 属性或 `NANZHUFENG_BUILD_PYTHON` 环境变量。
- 当前 Mac 已安装 `/opt/homebrew/bin/python3.13`；`android/local.properties` 只保存本机 SDK 路径且不进入 Git。
- 用户级 Gradle 旧显式代理会与当前 Clash/TUN 链路产生 TLS 握手失败；原配置已备份为 `~/.gradle/gradle.properties.before-nanzhufeng-android-20260716.bak`，当前构建使用现有系统网络路径。
- 当前网络下 Gradle 并发下载偶有 TLS 中断；首次拉取新依赖时使用 `--max-workers=1` 已稳定越过，不能把瞬时网络错误写成代码失败。

### 外屏历史筛选

- `HistoryFilters` 已从横向滚动行改为状态、平台、日期三个 `FlowRow`。
- 380dp Compose 仪器测试已覆盖筛选容器、“全部”、“全部平台”和“近 30 天”可见性。
- 专用模拟器为 `NanzhufengFindN5Api35`；本轮实际序列号 `emulator-5556`。现有 `ExpenseCapture_API35` 未被用于本项目测试。

## 验证等级

- 已实现：Mac Chaquopy 路径可配置；历史筛选使用可换行布局。
- 定向契约通过：`HistoryScreenInstrumentedTest.outerScreen_keepsAllFilterGroupsVisibleWithoutHorizontalScrolling` 在 `NanzhufengFindN5Api35`（API 35）通过。
- JVM 测试通过：`./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest`。
- 构建通过：`./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug`，`BUILD SUCCESSFUL`。
- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`，约 59MB，SHA-256 `8704f779cb37ead7d8a3c4c0aedfdfaefe8df3d727dd37aae3a78309f4bba7e2`。
- 模拟器视觉验证：360dp 近似窄屏上，状态、平台、日期筛选均完整换行显示，无横向滚动；空状态保持真实数据语义。
- OPPO 连接状态：设备 `PKH120` 已授权 ADB；本阶段没有向 OPPO 安装 APK、卸载应用或清空数据。

## 尚未完成，禁止宣称正式版落地

1. 真正 MP3 编码链路：当前普通音频源仍可能是 m4a/mp4，代码不能通过改扩展名伪装 MP3。
2. Find N5 外屏、内屏切换与状态连续性真机验收。
3. YouTube、抖音、TikTok 单视频和作者/频道/播放列表真实读取、过滤、分页与下载。
4. 取消勾选不下载、有效已有文件跳过、断网恢复、用户暂停不自动恢复、完成通知与历史归档。
5. 正式版本号、AGP/compileSdk 35 兼容性、Release APK/AAB 与覆盖安装验收。

## 下一阶段唯一任务

为 `AUDIO_MP3` 建立真实编码所有权：Android 解码音频为 PCM，LGPL LAME 原生库只负责编码 MP3，Repository/DownloadEngine 仍是任务状态与完成校验的唯一入口。先完成设计、许可边界和自动测试，再安装 NDK/CMake，不与平台真实下载或 OPPO 覆盖安装混做。

开始前必须读取：

- `docs/superpowers/specs/2026-07-16-android-formal-workbench-correction-design.md`
- `docs/superpowers/plans/2026-07-16-mac-build-history-filter-continuation.md`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DirectMediaTransfer.kt`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/OutputFilePolicy.kt`
