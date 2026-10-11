# 南枫下载 Android 当前交接

更新时间：2026-10-09（Asia/Shanghai）
当前分支：`main`

## 当前目标

按 `docs/superpowers/plans/2026-09-27-android-desktop-website-parity-plan.md` 对齐电脑版网站范围，优先 Pornhub。现有抖音修复与用户数据保持不动；本轮用户授权代码实现，未授权主设备安装或发布。隔离模拟器已用于测试，不能代替 OPPO 真实下载/播放验收。

### 当前增量：Pornhub 公开作品下载与导航密度（2026-10-09）

- 10-09 后续验收正在进行：正常队列已接回暂停与停止；樱花第 1 集实测暂停/继续后越过 100 MB，但断网暴露了状态仍停在“下载中”和清单重新选择导致缓存重下。当前已实现应用级网络等待、自动恢复开关、手动开始解除等待、HLS 原计划固定及旧 Frag 清理，Python 83 项通过；最新包运行复验仍未完成。樱花任务正常 UI 暂停在 68,051,252 B，没有最终成品。详情见 `docs/verification/2026-10-09-network-and-long-hls-recovery.md`；逐项审计见同目录 `2026-10-09-website-parity-completion-audit.md`。下方 APK 哈希与 300 项单测是此前 Pornhub 完成包的历史证据，不代表本段最新代码。

- 上轮把 Pornhub 网页回退出现的年龄页误判为下载前置条件。本轮对同一公开样本定位到 Android 嵌入 yt-dlp 的请求通道 410：主机同版 yt-dlp 可提取，隔离 AVD 的 Java/OkHttp/Python 标准库请求返回 200。现在只对官方单作品页及其 `phncdn.com` 媒体流启用兼容请求；未绕过账号、DRM、付费或年龄限制。
- 隔离 AVD 最终正式包经正常首页入口再次下载公开单作品 `viewkey=648719015`，Room 为 `COMPLETED`，MediaStore 成品 67,927,311 字节、1280×720。旧成品的 AAC 包带 ADTS 头而令内置播放器停在 0 秒；系统封装器现在逐帧校验并剥去 ADTS 头，多帧提取器采样拆成独立 AAC 帧。最终成品由 FFprobe 确认 H.264/AAC 及约 360.94 秒时长，FFmpeg 全片解码无错，内置播放器持续提交视频帧。先前 17,302,421 字节、426×240 是 360p 历史验收；详细证据与尚未验证的 OPPO、其他作品范围见 `docs/verification/2026-10-09-pornhub-download-playback-and-navigation.md`。
- 内外屏共用底部悬浮导航进一步收紧：外屏项目 52dp／内屏 56dp，外屏横向留白 32dp、内屏最大宽度 720dp，底部额外间距分别 4dp／6dp，系统手势安全区保留。隔离模拟器 1080×1920 与 1920×1920、420 dpi 检查了首页／设置和整块选中态；未做 OPPO 视觉验收。
- Python 77 项、Android Debug/Release JVM 各 300 项、`lintDebug`（0 error、35 warnings）和 Release R8/正式签名构建通过。当前本地签名 APK SHA-256 `05370785bb611667489b72f3ddd6560121141f42d0cd0bcb1a695291422751e8`，版本仍 `1.2.97 / 10297`，非主设备递增覆盖包。本轮未操作 OPPO、未推送或发布。其他 Pornhub 作品、樱花动漫最终下载及全部电脑版网站范围仍未验收。

### 当前增量：网站范围对齐代码与受控 Android 闭环（2026-10-08）

- Pornhub 单作品、通用 HTTP(S) 网页/直链/音频、HLS TS/fMP4/主清单及独立音轨、DASH、动态播放器捕获、多 URL 输入、通用合集/樱花动漫剧集规则均已接入现有 Repository → Room → Worker → MediaStore/SAF → 历史链。五个旧平台专用路由保留优先级，不共用会话；新增 Room schema 10 和 9→10 迁移。
- 受控 4 秒媒体在本轮新建的隔离模拟器完成正常入口的发现、选择、下载和媒体库回读；直链 MP4 在历史内置播放器显示画面，独立音轨 HLS 成品由 FFprobe 确认 H.264/AAC。签名 Release/R8 在临时模拟器完成 MP4、HLS 和合集所选成员的真实文件输出。公开 MDN MP4 也已由正式包按正常 UI 下载 1,128,375 B，并在历史播放、重启后回读。同源 `iframe srcdoc` 的动态播放源原先无法捕获；加入最多三层的可见同源 iframe 播放器检查后，正式包下载出 1,128,375 B 成品并在重启后保留历史；跨域 iframe 未因此自动可读。详见 `docs/verification/2026-10-08-website-parity-code-and-emulator.md`；首批 Pornhub/分段证据见同日 `pornhub-segment-transfer-increment.md`。
- 2026-10-08 追补：分段计划变更只清理本任务旧指纹缓存；Pornhub 官方单作品提取器在隔离模拟器返回 410 时，进入用户可见网页播放器回退，捕获仍保持 Pornhub + 原 `viewkey` 身份。403/DRM 不回退。主机 App 同源解析公开 yt-dlp 样本成功；模拟器网页打开后停在站点年龄确认页，未代用户确认、未播放或下载。合成 Room v9 旧库在隔离模拟器中实际升为 v10，旧媒体/历史保留；不是用户真库验收。细节见 `docs/verification/2026-10-08-website-parity-code-and-emulator.md`。
- 2026-10-08 动态外站追补：MDN `<video>` 文档的实际播放器位于两层开放 Shadow DOM 与跨域 iframe 内。修复后由用户选择可见 iframe、播放并读取，保留原页面标题和身份。首次真实任务因 VP8/Vorbis WebM 被误强制封装 MP4 而失败；现在完整 WebM 保留 `.webm`/`video/webm`，原任务界面重试后媒体库回读 554,058 B、5,059 ms，历史内置播放器显示画面，强制停止重启后仍可播放。两层 Shadow DOM 同源 `srcdoc` 动态视频夹具也完成 1,128,375 B MP4 下载；空 `srcdoc` 旧包错误列出当前页播放器，最终包已失败关闭。WebM 多段拆分仍明确不支持，其他外站与站点特有限制没有因此自动通过。证据同上。
- 2026-10-08 樱花动漫实际页面追补：`yhdm.one/vod/2025999379.html` 的正常网页入口列出 12 集；旧版把所选剧集页直接加入队列后，下载失败为 `Unsupported URL`。现改为所选剧集逐项打开可见播放器，只有用户播放并确认当前源后才入队；未确认或页面身份不匹配时不造任务。捕获源沿用原 `collection:` 身份，能刷新并重排同一条失败任务，避免新增重复任务。隔离 AVD 数据库只读回查确认既有失败条目变为 `WAITING` 且保存了捕获源，任务总数仍为 9；尚未执行该站最终文件下载/播放验收。
- 2026-10-08 内屏导航追补（历史状态，已被 10-09 改动取代）：展开态移除旧 80dp 左栏，主页／历史／设置改为底部独立白色圆角悬浮栏，保留整块选中态，页面重新使用完整宽度；当时紧凑态原底部导航不变。隔离 AVD 在 1920×1920、420 dpi 展开视口检查三页，另在 1080×1920、420 dpi 检查紧凑首页；未做 OPPO 内屏实机视觉验收。下方 v1.2.85 左栏段落仅是当时的历史记录。
- 2026-10-09 导航及文案追补：内外屏共用底部独立悬浮白色圆角导航；阴影降为 6 dp 并减淡环境及投影色，底部“主页”统一改名“首页”，展开首页标题下的说明小字移除。隔离 AVD 正式包分别在 1080×1920 与 1920×1920、420 dpi 首页目视确认布局和标签；尚无 OPPO 内外屏视觉验收。
- 2026-10-09 自动验证：Debug/Release JVM 各 294 项、`lintDebug`、Release R8/签名构建通过；本次未重跑 Python 73 项（此前记录），未运行任何 `connected*AndroidTest`。当前签名 APK SHA-256 为 `9097ecc15ffb86f3acd51ab9532aaed42db0c986f3616d103c2b4812f156505e`。旧测试数量和 APK 哈希仅属历史记录。
- 版本仍是 `1.2.97 / 10297`。本轮仅在新建的临时 AVD 安装测试包；未触碰 OPPO，未提交、推送或发布。HEAD `d9a66a1` 加已有未提交工作区，旧抖音/设置修改须保留。
- 此段是 10-08 的历史未完成状态；10-09 已完成一个公开 Pornhub 作品在隔离 AVD 的下载与播放验证，见本文件顶部。樱花动漫及更多代表性外站作品页、Pornhub 其他作品和 OPPO 仍未完成最终文件验收，不能宣称电脑版网站范围已全面对齐。OPPO 正式覆盖与 GitHub 发布另需授权。

### 当前增量：抖音目标视频真机下载（v1.2.97，已覆盖，2026-09-23）

- 用户样本 `https://v.douyin.com/qiRt29Dauu4/` 与设备输入中的同作品短链均指向作品 `7686854662317722889`。OPPO 的设置内抖音网页能打开已登录账号；此前“要求重新登录”不是账号未保存的证据。
- v1.2.91 仍在目标页超时。v1.2.95 虽能建任务，却仅凭目标页的预加载媒体请求误认视频，标题成为“抖音作品 + ID”，实际下载报“视频不可用”。在 v1.2.96 真机诊断中，完全透明的 WebView 改为可见后，页面显示目标视频、标题和作者，`<video>` 才出现。v1.2.97 使用 0.01 alpha 保留渲染、只接受目标页视频元素的流地址，并优先读取作品正文标题；官方 `/share/video/` 链接也会规范化到 `/video/`。
- 用户授权下，v1.2.97 正式同签名 APK 保数据覆盖 OPPO；本地与设备回拉 APK SHA-256 均为 `ba2cd047c7ce85cf1e9efd01c49e327996c8dd01cbe88ab529d10998893b4ce4`，首次安装时间与 CE／DE 数据 inode 未变。Debug／Release 定向 JVM 测试、Python 48 项及 Release 签名／lintVital／R8 门禁通过；未运行 `connected*AndroidTest`。
- 已在 OPPO 正常首页执行“智能读取 → 开始下载”，App 弹出“下载成功”，完整标题以“你的Agent不是笨，是没记性”开头；系统媒体库只读查询确认本次 MP4 为 26,838,367 字节、媒体时长 553,334 ms（约 09:13），与目标网页显示的 09:13 一致。没有点开最终文件做人工播放验收；用户切到另一 App 后停止了 UI 操作。作者仍回退为“抖音用户”，文件名仍以“未知日期”开头；它们是元数据待改进项，不能说成已正确。
- 详细证据与边界见 `docs/verification/2026-09-23-android-v1.2.97-douyin-device-download.md`。本轮未提交、推送或发布 GitHub。以下 v1.2.91 及更早版本是历史增量，不代表当前安装版本。
- 收尾时 OPPO 从 ADB 断开，本轮 `/data/local/tmp/` 中的 v1.2.92–v1.2.97 临时 APK 与一张临时截图未完成精确清理；待设备再次连接后清理明确路径，不清数据、不动已下载 MP4。

### 当前增量：抖音已存会话被误判为须重新登录（v1.2.91，已覆盖，2026-09-23）

- 用户样本短链 `https://v.douyin.com/qiRt29Dauu4/` 可解析为官方 `https://www.douyin.com/video/7686854662317722889`。旧发现链对该作品页先调用 yt-dlp；其 `fresh cookies` 错误在目标页捕获之前被翻译为“重新登录”。设置页的 Cookie 状态只说明存在认证 Cookie，不能证明本次网页请求已登录，也不能据此认定账号未保存。
- 现在官方视频／图文作品页直接进入既有目标页捕获；视频页不得把封面图误作图集完成。已有抖音认证 Cookie 时，“查看账号”打开账号页供用户核对，而非无条件跳转登录页。
- Python 47 项测试、相关 Android Debug／Release JVM 测试、Release 构建及签名／R8 门禁通过。隔离的未登录模拟器走正常首页入口时不再提前报“重新登录”，也不再错误加入封面图任务；该模拟器网页未返回视频流，故没有完成真实视频下载。
- 正式版 `1.2.91 / 10291` 已以同签名 `pm install -r --user 0` 覆盖 OPPO。回拉设备 `base.apk` 与本地正式 APK SHA-256 同为 `0014ad5f1f238e6f109b1c347e6ef1d7716395b6dc71dc7c2e3fbd14e9cd1eea`；首次安装时间及 CE／DE 数据 inode 未变。未自动启动或操作 OPPO App；已登录账号对该样本的读取和下载仍待用户手动复验。未提交或上传 GitHub。
- 详细证据及未确认项见 `docs/verification/2026-09-23-android-v1.2.91-douyin-session-discovery.md`。以下旧版本记录是历史增量，不代表当前安装版本。

### 当前增量：视频详情操作面板与播放器手势／安全区（v1.2.87，已覆盖，2026-09-01）

- 历史页的“视频详情”不再使用会在展开屏留下大块空白的默认 `AlertDialog`。现在是限宽 600dp、最高 620dp 且内容可滚动的白色详情面板：顶部以媒体类型图标、标题和关闭入口建立层级；作品标题、作者、平台／清晰度、时长和真实文件大小集中在浅色信息面板中。它没有新增或伪造业务数据。
- 可播放媒体的“内置播放器播放／查看图片”是唯一主操作；视频外部打开、复制原链接、查看吞吐报告均保留原有回调，改为对应的图标浅底按钮。文件不可读时，主播放按钮按原逻辑不显示，复制链接和已有报告入口仍可用。
- 内置视频播放器接入双击切换播放／暂停；触摸监听把事件继续交回 `PlayerView`，单击控制条、拖动和关闭控制不被吞掉。视频渲染 View 现在应用 `statusBarsPadding()`，根层状态栏区域保持黑底，画面从系统状态栏以下开始；标题控制层仍随 Media3 控制条显隐。
- 自动验证：`HistoryDetailsDialogContractTest`、`InternalVideoPlayerChromeContractTest` 的 Debug／Release JVM 回归，以及 Debug／Release lint 均通过；`stageFormalReleaseArtifacts` 的 Release 签名门禁、R8 `verifyReleaseParcelableCreatorRetention` 门禁、正式 APK／AAB 均通过。Gradle 常规依赖校验仍受本地 `junit-bom-5.9.3.module` 缓存与校验元数据哈希不匹配阻断；本轮仅离线使用既有缓存并对构建命令临时关闭该校验，未改动仓库校验文件。
- 正式 APK：`android/app/build/outputs/formal-release/南枫下载-Android-v1.2.87.apk`，46,055,272 B，SHA-256 `304ced0e2789267887f42cd8be7363321914795cf11a6984eb91b7dbc727f7bc`；包名 `com.nanzhufeng.videodownloader`，版本 `1.2.87 / 10287`，证书 SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。
- 用户授权后已以 `pm install -r --user 0` 覆盖 OPPO 正式包。安装前为 `1.2.86 / 10286`，安装后设备 `base.apk` 与上述本地 APK 的 SHA-256 相同、证书相同；首次安装时间仍为 `2026-08-21 02:04:16`，CE／DE 数据 inode 仍为 `1788131`／`1745695`。未启动 App、未操作用户下载记录、未运行任何 `connected*AndroidTest`；设备和本地临时安装 APK 已清理。
- 尚未对 v1.2.87 的内外屏详情面板、视频状态栏留黑和双击播放／暂停进行人工视觉／手势确认；未重新执行用户手动冷启动复验。源码提交 `2130e1b` 与标签 `v1.2.87` 已推送 GitHub；对应 Release 草稿已创建，但 GitHub 附件上传无字节进度后被停止，草稿当前无附件，不能视为下载交付。经核验的 APK 仍保留在 `<local-reference>`，SHA-256 为 `304ced0e2789267887f42cd8be7363321914795cf11a6984eb91b7dbc727f7bc`。

### 当前增量：设置成对卡片等高与输入计数让位（v1.2.86，已覆盖，2026-09-01）

- 展开设置页的前两行改为成对行容器：账号与权限／下载质量、下载规则／保存位置分别以较高卡片的内容高度为整行高度，左右卡片都填满该高度；较矮的一侧仅补白，不删除、截断、折叠或移动任何设置内容。功能审阅仍保留左半列，紧凑单列设置页不变。
- 账号与权限各平台的“清除”均改为无黑线框的浅灰实底按钮；“导入／登录／继续登录”等绿色主操作不变。
- 添加任务的字符数从输入框右侧移至输入框下方、右对齐的单独一行。`ReadEntryCard` 同时被展开右栏和紧凑底部入口复用，因此内外屏都释放输入框右侧宽度给链接文字；复制、智能读取、清空、字符上限和键盘避让逻辑不变。
- 自动验证：`SettingsExpandedCardAlignmentContractTest`、`HomeLayoutResourceTest`、`NavigationRailLayoutContractTest`、`WorkbenchPaletteContractTest` 的 Debug/Release JVM 回归以及 Debug/Release lint 均通过；`stageFormalReleaseArtifacts` 的 Release 签名门禁、R8 冷启动 `CREATOR` 保留门禁、APK/AAB 产物均通过。Gradle 常规依赖校验仍受本地 `junit-bom-5.9.3.module` 缓存与校验元数据哈希不匹配阻断；本轮仅离线使用既有缓存并对构建命令临时关闭该校验，未改动仓库校验文件。
- 正式 APK：`android/app/build/outputs/formal-release/南枫下载-Android-v1.2.86.apk`，46,038,888 B，SHA-256 `fc0da075cee1b25437ebfcd9af75f851dcc8037a07c3bede3230883241ba0e83`；包名 `com.nanzhufeng.videodownloader`，版本 `1.2.86 / 10286`，证书 SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。
- 用户授权后已以 `pm install -r --user 0` 覆盖 OPPO 正式包。安装后设备 `base.apk` 与上述本地 APK 的 SHA-256 相同，证书相同；首次安装时间仍为 `2026-08-21 02:04:16`，CE/DE 数据 inode 仍为 `1788131`／`1745695`。未启动 App、未操作用户下载记录、未执行任何 `connected*AndroidTest`；设备和本地临时安装 APK 已清理。
- 尚未对 v1.2.86 的内外屏进行人工视觉确认，也未重新执行用户手动冷启动复验；未创建 GitHub Release。

### 历史增量：侧栏仅在自身栏位内居中（v1.2.85，已覆盖，2026-09-01）

- `NavigationRail` 现在显式固定为 80dp 宽；内部填满容器只会填充这块左栏，三个入口的 `Alignment.Center` 仅相对左栏自身高度生效，右侧页面内容不再被侧栏撑走。顶部应用图标和紧凑布局底部导航不变。
- v1.2.84 的实现错误地让侧栏内部 `Box.fillMaxSize()` 参与无横向上限的测量，截图已确认它撑满整个屏幕并把入口组置于屏幕中央。原交接中“侧栏居中已完成”的结论已作废；新的布局契约额外锁定 `.width(80.dp)`。
- 全部最外层白色工作台卡片继续复用 `WorkbenchCard`，已统一移除硬边描边，保留圆角与 1dp 浅阴影。添加任务的链接输入框和“清空”按钮保持无描边浅灰实底；复制、字数、键盘避让、智能读取和清空逻辑不变。
- 自动验证：`NavigationRailLayoutContractTest`、`WorkbenchPaletteContractTest`、`HomeLayoutResourceTest` 的 Debug/Release JVM 回归与 Debug/Release lint 均通过；`stageFormalReleaseArtifacts` 的 Release 签名门禁、R8 冷启动 `CREATOR` 保留门禁、APK/AAB 产物均通过。Gradle 常规依赖校验受本地 `junit-bom-5.9.3.module` 缓存与校验元数据哈希不匹配阻断；本轮仅离线使用既有缓存并对构建命令临时关闭该校验，未改动仓库校验文件。
- 正式 APK：`android/app/build/outputs/formal-release/南枫下载-Android-v1.2.85.apk`，46,038,888 B，SHA-256 `4b39df7379f03c51ff396eefa725e34f87d478d120b1c780e339e2cf9dea9b8f`；包名 `com.nanzhufeng.videodownloader`，版本 `1.2.85 / 10285`，证书 SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。
- 用户授权后已以 `pm install -r --user 0` 覆盖 OPPO 正式包。安装后设备 `base.apk` 与上述本地 APK 的 SHA-256 相同，证书相同；首次安装时间仍为 `2026-08-21 02:04:16`，CE/DE 数据 inode 仍为 `1788131`／`1745695`。未启动 App、未操作用户下载记录、未执行任何 `connected*AndroidTest`；设备和本地临时安装 APK 已清理。
- 尚未做 v1.2.85 的 OPPO 人工视觉验收，也未重新执行用户手动冷启动复验；未创建 GitHub Release。

### 历史增量：工作台表面去硬边与错误侧栏实现（v1.2.84，已被 v1.2.85 修正，2026-09-01）

- v1.2.84 的白卡和添加任务控件材质调整仍保留，但侧栏居中实现因横向测量边界错误而不可用；完整的错误产物、安装回读和限制见 `docs/verification/2026-09-01-android-v1.2.84-workbench-surface-and-oppo-overlay.md` 顶部更正说明。

### 当前增量：冷启动 Compose 状态恢复崩溃（未发布，2026-08-24）

- OPPO 只读系统崩溃记录确认：从 v1.2.57 至 v1.2.82 多次在进程启动约 0.2–0.4 秒后因 `BadParcelableException` 退出；根因是 R8 移除了 `androidx.compose.runtime.ParcelableSnapshotMutableState` 的反射恢复字段 `CREATOR`，冷进程恢复 Compose `rememberSaveable` 状态时触发 `NoSuchFieldException: CREATOR`。这不是下载、Python、Room 或用户数据问题。
- Release 现在加载 `proguard-android-optimize.txt` 与项目 `proguard-rules.pro`，并精确保留该 Compose 状态类的 `CREATOR`。新增 `verifyReleaseParcelableCreatorRetention`：Release 映射缺少 Creator 初始化路径时，`assembleRelease` 会失败，防止同类冷启动崩溃再次进入正式包。
- 代码与构建证据见 `docs/verification/2026-08-24-android-cold-start-parcelable-state.md`。Debug/Release JVM 单测、Debug/Release lint、Release R8/签名与门禁均已通过；正式 APK DEX 已直接确认该类含 `public static final Parcelable.Creator CREATOR`。
- 已在用户授权下以 `pm install -r --user 0` 覆盖到 v1.2.83 / 10283；设备 `base.apk` 与本地正式 APK 的 SHA-256 同为 `bcb77dde446fdb066399f6204a111740a4e3d9cac80213f20d4e666e274236a9`，证书仍为 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。首次安装时间和 CE/DE 数据 inode 仍为既有值。未自动启动或操作 OPPO 用户任务，未运行任何 `connected*AndroidTest`，也未发布 GitHub 新版本；下一步只需用户手动执行“从桌面冷启动 → 返回桌面/等待系统回收 → 再次冷启动”复验。

### 当前增量：抖音 `/note/` 图集来源与传输完整性（v1.2.82，2026-08-23）

- 用户目标是让抖音图文走完“智能读取 → 完整非水印图集 → 下载 → MediaStore”，不是给失败增加限制。此次修复了五个实际断点：`yt-dlp`/直连接口会覆盖页面原始图源、拦截链会再次丢失正确结构化列表、CDN 会无请求地只给部分 `206`、空响应或截断 WebP 会被误当有效文件、重定向可转向 `tplv-dy-water` 水印源。
- `/note/` 现在只走目标页捕获；从当前作品的 React Flight `aweme.detail.images[].urlList` 直接取完整列表并逐项拒绝 `tplv-dy-water`。下载端统一以桌面 UA 请求，遇到无请求的 `206` 会续传到服务端声明总长度；空体、截断 RIFF/WebP 及最终重定向到水印 URL 都会失败而非写入成品。抖音签名图集按顺序传输，避免并发请求破坏同一作品的完整交付。
- 隔离模拟器已按正常 UI 路径验收两个公开样本：`7676041925425736777` 最终写入 MediaStore 2/2 张（2160×2880、2112×2160），两张均可解码且人工检查无抖音号水印；`7670887343922973155` 最终写入 14/14 张，14 张均可解码。未使用 deep link、数据库写入、Activity extras 或 `connected*AndroidTest`。
- 正式 APK：`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.82.apk`，45,973,348 B，SHA-256 `758e2cf7260f90501d5dbfb3be1fd4c0f92eb9d116e665bfa8945183e1aac6b7`；证书 SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。
- OPPO Find N5 已由 1.2.81/10281 同签名覆盖至 1.2.82/10282；首次安装时间仍为 `2026-08-21 02:04:16`，CE/DE 数据 inode 仍为 `1788131`／`1745695`，回拉设备 `base.apk` 与上述本地产物哈希一致。用户随后确认已在 OPPO 完成抖音图文真实下载闭环，14 张样本逐张人工视觉检查无水印；本条只记录用户确认，未补写不存在的设备命令或媒体元数据。
- 最终自动回归、完整证据矩阵与 checkpoint 以 `docs/verification/2026-08-23-android-v1.2.82-douyin-gallery-integrity.md` 为准；外部风险仍是平台页面、CDN 和反爬规则变化，后续新样本仍需独立验收。


## 历史归档

本文件只保留当前任务与最近有效证据。完整历史已原样保存在 [历史交接归档](docs/archive/PROJECT_HANDOFF_HISTORY_THROUGH_20260901.md)；仅在追溯旧决策或旧证据时按关键词读取，不作为当前状态全文加载。

归档 SHA-256：`341e459f4bea86cea07b16edda6090f815de15641df8cb074e50c457783135bb`
