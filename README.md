# 南枫下载 Android

Android 视频下载工作台，支持链接读取、下载队列、系统媒体库保存、历史收藏和内置播放。

[下载 v1.2.163 正式版](https://github.com/nanzhufeng/NanfengDownload-Android/releases/tag/v1.2.163) · [源码](https://github.com/nanzhufeng/NanfengDownload-Android) · [项目状态](docs/PROJECT_STATUS.md)

## 界面预览

| 首页 | 历史 |
| --- | --- |
| <img src="docs/screenshots/github-preview/home-v1.2.163.png" width="260" alt="南枫下载163首页" /> | <img src="docs/screenshots/github-preview/history-v1.2.163.png" width="260" alt="南枫下载163历史白色胶囊工具栏" /> |

| 设置 | 后台下载岛展开 |
| --- | --- |
| <img src="docs/screenshots/github-preview/settings-v1.2.163.png" width="260" alt="南枫下载163分组设置菜单" /> | <img src="docs/screenshots/github-preview/island-expanded-v1.2.163.png" width="260" alt="南枫下载163模拟器后台下载岛展开" /> |

均为 v1.2.163 精确正式 APK 的独立 Android 35 模拟器截图：前三页1080×2400/420dpi（2026-11-01），后台岛1140×2616/442dpi（2026-10-29），字体1.0；只含自制测试视频，无私人账号或媒体。后台岛截图不能代替ColorOS原生岛验收。[截图与安装包对应信息](docs/screenshots/github-preview/v1.2.163.json)。

## 功能

- 本地视频预览使用多个时间点避开黑色片头，并更新旧预览缓存。
- 清理失效一次检查全部已完成历史，不受搜索或滚动限制；保留可读和无法确认的记录，不删除媒体文件。
- 平台、时间、收藏与批量删除、清理失效使用白色胶囊同排显示。
- 失效清理复用同目录检查结果，支持保存文件夹被删除后的历史清理；无法确认的文件仍保留。
- 设置使用分组菜单，平台登录会话导出在独立页面选择。

- 修复部分 HLS 视频因起始 AAC 时间戳被压为零而封装失败的问题，保留完整音视频样本。

- 新链接可直接粘贴替换旧输入；切换输入时取消旧读取，隔离迟到的页面捕获结果。

- 读取 YouTube、抖音、TikTok、哔哩哔哩、小红书，以及已接入的 Pornhub 单作品和通用 HTTP(S) 媒体页面；平台入口范围与实际验收见[网站审计](docs/verification/2026-10-10-five-platform-read-download-audit.md)。
- 按画质下载或导出 MP3，支持有界多连接、分段传输、续传、暂停、停止与失败重试；最终校验后保存到媒体库或所选目录。
- 后台通知和灵动岛显示真实任务进度，提供简洁／详细视图及查看／停止操作。置顶需用户在系统无障碍设置手动开启“南枫下载灵动岛置顶”；服务只绘制进度，不读取屏幕或执行手势。未开启时普通悬浮显示在状态栏下方。
- 历史视频收藏，支持关键词、平台和时间筛选；取消收藏保留视频文件。
- 内置视频播放器保存播放位置；左右双击快退／快进10秒，连续点击叠加，保持播放状态。
- 手机窄屏底部导航，展开屏使用双栏工作区；菜单统一配图标。

## 安装与边界

Release 附件使用稳定英文名 `NanfengDownload-Android-v1.2.163.apk`，只提供安装包。本地正式构建产物保留中文名。长期正式签名相同的版本可保数据覆盖；签名不匹配时停止，禁止以卸载或清数据绕过。

只处理公开内容或用户有权访问的内容，不绕过付费、DRM或私密限制。读取与下载速度受平台、CDN、网络和账号条件影响，不承诺所有平台每次秒读或固定吞吐。OPPO已于2026-10-29核验163同签名覆盖及限定媒体元数据保留，业务真机路径仍待用户验证；模拟器成功不能代替主设备或全部公网平台验收。

## 开发与验证

Kotlin / Jetpack Compose / Room11 / WorkManager / OkHttp / Media3 / Chaquopy / yt-dlp；Android 10及以上，compile/targetSdk35。

正式签名优先完整 `NANFENG_RELEASE_*` 环境变量；备用为仓库外用户级 Gradle 的 App 专属命名空间，不提交凭据。

```bash
cd android
./gradlew testDebugUnitTest testReleaseUnitTest lintRelease assembleRelease
```

产物：`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.163.apk`。配置所需JDK、Python、NDK及签名后构建，详见[开发档案](docs/南枫下载%20Android%20完整开发档案.md)。宿主 Python 测试设置 `PYTHONDONTWRITEBYTECODE=1`，避免把宿主缓存写进Android依赖目录。

永久禁止 `connected*AndroidTest` 和主设备Debug／仪器部署。正式主设备覆盖只按用户授权核验同签名、递增版本和数据指纹。

## 文档

- [项目状态](docs/PROJECT_STATUS.md)：当前能力、门禁、验证缺口和同步情况。
- [当前交接](PROJECT_HANDOFF.md)：源码、冻结包与下一步边界。
- [163发布验收](docs/verification/2026-11-01-release-163.md)：冻结包、发布回归、当前预览与GitHub交付证据。
- [后台灵动岛](docs/background-download-island.md) · [播放器](docs/video-playback.md) · [历史收藏](docs/history-favorites.md)

维护与升级先读[现行合同总入口](docs/contracts/README.md)，再核对PROJECT_STATUS与PROJECT_HANDOFF；项目根运行 `python3 tools/check_contracts.py` 检查合同路由/源码漂移。结构检查不替代业务验收。
