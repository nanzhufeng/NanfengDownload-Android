# 南枫下载 Android

面向 Android 的 YouTube、抖音、TikTok 视频下载工作台。支持公开单视频与已结束直播回放的读取、去重、队列管理、真实下载、音视频合并、MP3 转码、系统媒体库发布、完成历史与永久吞吐报告。

> 当前是 `0.1.0-probe` Debug 真机验收版，尚未配置正式 Release 签名、版本号与 AAB 发布链路。

## 界面预览

| 首页 | 历史 | 设置 |
| --- | --- | --- |
| <img src="docs/screenshots/github-preview/home.png" width="300" alt="南枫下载 Android 首页" /> | <img src="docs/screenshots/github-preview/history.png" width="300" alt="南枫下载 Android 历史" /> | <img src="docs/screenshots/github-preview/settings.png" width="300" alt="南枫下载 Android 设置" /> |

截图来自当前最终 APK 在项目专用 Android 模拟器中的真实运行页面，统一使用 OPPO Find N5 外屏基准 `1140×2616 / 442dpi / font scale 1.0`。每张图片只展示一个页面，不使用旧设计稿或多页面拼接图冒充实际界面。

## 核心能力

- 支持粘贴或分享 YouTube、抖音、TikTok 链接，公开单视频优先无需登录使用。
- 下载列表持久保留等待、下载、失败、取消与重复跳过状态；失败提供中文原因、解决建议、重试和删除。
- 平台感知的 Range 探测与单/多连接传输，保存实际连接模式、网络字节、平均/峰值速度、重试和回退原因。
- 支持最佳画质、1080p、720p、360p 与仅音频 MP3；真实 MP3 由 Android 解码 PCM 后通过 LAME 4.0 编码。
- 视频写入系统 `Movies`，MP3 写入系统 `Music`；完成历史支持打开、分享、复制与删除。
- 账号与权限页支持 YouTube Cookie 导入及抖音、TikTok WebView 会话；登录状态以真实有效会话验证，不以页面退出冒充成功。
- 适配 OPPO Find N5：外屏单页底部导航，内屏单页左侧导航与双栏工作区。

## 产品边界

- 只处理公开内容，以及用户已合法登录并有权访问的内容。
- 不绕过会员、付费、DRM 或私密内容限制。
- 不在仓库中保存账号密码、Cookie、Token、签名密钥或用户媒体。
- 平台 CDN、网络、地区和账号条件会影响速度与成功率，界面只报告真实测量结果。

## 技术栈

- Kotlin、Jetpack Compose、Material 3、Navigation Compose
- Room、DataStore、WorkManager、OkHttp、Media3
- Chaquopy Python 3.13 + `yt-dlp`
- Android NDK/CMake + LAME 4.0 JNI
- `minSdk 24`、`compileSdk 35`、`targetSdk 35`

完整版本与架构见 [context.md](context.md)。

## 构建与验证

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1
```

构建产物直接命名为：

```text
android/app/build/outputs/apk/debug/南枫下载.apk
```

自动化 UI 测试必须绑定项目专用模拟器。OPPO 真机更新固定使用“推送 APK → `pm install -r --user 0`”的同签名覆盖路径，不使用会触发 ColorOS 图形确认页的直接 `adb install`。

## 项目文档

- [context.md](context.md)：当前技术栈、目录结构、数据流、构建与风险入口。
- [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md)：真实设备、三平台下载、吞吐报告和阶段验收证据。
- [design-qa.md](design-qa.md)：外屏/内屏三页 UI 合同与视觉验收。

## 发布状态

当前仓库明确维护 Android 版。正式对外发布前仍需完成 Release 签名、正式版本号、可重复打包、Release APK/AAB 与覆盖升级验证。
