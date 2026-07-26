# 南枫下载 Android 当前交接

更新时间：2026-07-26（Asia/Shanghai）
当前分支：`codex/android-ui-download-core-checkpoint-20260718`

## 当前目标

把 Android 正式工作台完整落地到 OPPO Find N5，并以真实设备上的正式用户路径验收为结束条件。模拟器、构建或 APK 生成不能替代 OPPO 真机结论。

## 已完成阶段

### Android v1.1.0 哔哩哔哩与小红书扩展（2026-07-26）

- 新增两个平台的链接识别、短链、去重、解析、平台图标、下载策略、MediaStore 目录、历史、吞吐报告与中文错误闭环。
- 修复 OPPO 当前 Clash/DNS 环境下 `b23.tv` 被导向证书不匹配节点的问题：短链解析使用 Cloudflare/Google 可信 DNS、严格 TLS 和官方域名校验；系统 DNS 仅作为受控回退。
- 补齐小红书当前 Android App 使用的 `xhslink.cn` 分享域名；链接分类、会话归属、请求头和短链官方跳转校验统一支持 `.cn` 与旧 `.com`。
- 专用模拟器真实完成哔哩哔哩公开视频 `BV1bK411W797` 和小红书/Rednote 视频笔记 `69ce30d3000000002100791c` 的读取、下载、成品入库与历史记录。
- 哔哩哔哩 UP 主页批量接口当前返回 412，明确收紧为单视频支持；小红书公开单视频无需登录，失效的移动网页登录入口已移除。
- Python 18 项、Debug/Release JVM 各 145 项、Release Lint、模拟器仪器测试 59 项均通过。
- 正式验收版为 `1.1.0 / versionCode 10100`；APK SHA-256 `db3eb8d9525272d506c03b0933de8478793006525b6a9ffd70048facdb11c4dd`，AAB SHA-256 `3e9b78c00e99cc4aa1a9b541d240d49c97ec9ea58b2b0baf2e018f6d0aec9fb2`。
- OPPO `PKH120` 已由 `v1.0.0 / 10000` 同签名无损覆盖到 `v1.1.0 / 10100`；`firstInstallTime` 仍为 `2026-07-18 13:44:10`，旧历史与输入草稿保留，手机回读 APK 与本地产物 SHA-256 一致。
- OPPO 公开单视频真实结果：哔哩哔哩选择“720p 及以下”预设，成品实际为 852×480 HEVC + AAC、7.5 MB、单连接、平均 3.8 MB/s、峰值 19.4 MB/s；小红书成品为 1280×720 H.264 + AAC、10.7 MB、多连接 ×3、平均 4.3 MB/s、峰值 21.8 MB/s。
- 同一条此前失败的 B 站分享短链 `https://b23.tv/kdX9kKW` 已在 OPPO 完成读取、入队、下载、MediaStore 和历史闭环；成品 15,143,202 B，单连接、平均 1.4 MB/s、峰值 2.8 MB/s。
- OPPO 追加通用性验证：哔哩哔哩 3 个不同公开视频、小红书 3 个当前实时分享视频全部完成读取、入队、下载、系统媒体文件、历史与重启持久化；6/6 成功，覆盖单连接、多连接、483 KB 至 26.8 MB 和多分 P 内容。
- 历史页“全部平台 / 全部时间”已收进同一行的两个菜单按钮；OPPO 外屏真机首屏可见更多时间线视频。
- 两个成品均由 OPPO MediaStore 读回；重复小红书链接被去重，强制停止并重启 App 后两条历史和吞吐报告仍在。GitHub `v1.1.0` 尚未上传或发布。
- 详细证据见 `docs/verification/2026-07-26-android-v1.1.0-bilibili-xiaohongshu.md`。

### Android v1.0.0 正式发布收口（2026-07-21）

- 版本正式化为 `1.0.0 / versionCode 10000`，最低系统提升到 API 29，避免 API 24–28 的 MediaStore 输出路径不可用。
- 建立独立 Release 签名，密钥与密码均在仓库外保管；APK/AAB 证书 SHA-256 为 `C4:FB:47:E2:76:B5:A9:38:1E:53:62:E8:D1:76:CC:B9:E1:71:A0:34:F5:13:C9:D8:11:D4:7A:53:64:0F:45:47`。
- `testDebugUnitTest` 与 `testReleaseUnitTest` 各 127 项、0 失败；`lintRelease` 通过；正式门禁 117 个任务全部执行，`BUILD SUCCESSFUL in 2m 31s`。
- 正式 APK 为 91,291,547 B，SHA-256 `460a310aa8bfa50ae5cbc4683682c858b0dbc040659bc9300925874a8b88cbca`；正式 AAB 为 38,245,189 B，SHA-256 `70f62007511626186330856765edebb30d269a1d5b0e1578234ad90613b4bab3`。
- APK 签名、不可调试标志、版本、SDK、双 ABI 和 ZIP 完整性均通过；AAB 的 JAR 签名、ZIP 完整性和 Google bundletool 1.16.0 验证通过。
- Release APK 已在专用模拟器冷启动；拉回 `base.apk` 与本地产物哈希一致。OPPO 保留旧 Debug 安装和数据，本轮没有卸载或覆盖。
- 详细证据见 `docs/verification/2026-07-21-android-v1.0.0-release.md`。

### 外屏键盘可达性（2026-07-21）

- 未打开键盘时保留既有固定首页布局：下载列表继续占满剩余空间，“添加任务”卡片固定在底部，不因键盘适配而改变平时位置。
- 键盘弹出时读取真实 IME 高度和“智能读取 / 清空”操作区的实际坐标，仅上移到整排按钮完整位于键盘上方；收起键盘立即归零位移，不叠加 `imePadding`，不产生灰绿色空白或遮挡层。
- OPPO Find N5 外屏已用真实搜狗键盘完成开合对照，用户确认最终位置正确；`testDebugUnitTest`、`lintDebug`、`assembleDebug` 完整门禁通过。

### 最终回归与文档固化（2026-07-21）

- 当前代码 checkpoint：`5949206`（`fix(android): keep home actions reachable above keyboard`）；该提交只包含本项目 6 个相关文件，未混入其他项目或未知改动。
- 以当前未提交工作区为对象强制重跑标准门禁：127 项 JVM 测试、0 失败；`lintDebug` 通过；`assembleDebug` 双 ABI 构建通过，69 个任务全部实际执行。
- 专用模拟器 `emulator-5580` 保持 OPPO 外屏基准 `1140×2616 / 442dpi / font scale 1.0`；完整仪器测试 58 项、0 失败。测试 APK 仅通过指定序列号安装到模拟器，未接触 OPPO。
- 旧设置页紧凑度测试对两张等高卡片使用 28% 硬阈值，真实值均为 28.36%；统一改为 29% 容差并记录真实比例，生产 UI 没有修改。
- 最后一次完整门禁后的唯一最终 APK 为 68,046,080 B，SHA-256 `01b01b2386934b82a12b87e3c4f3466a786c6426b20f0ebf48a4d411d43659df`；OPPO 按“推送 APK → `pm install -r --user 0`”同签名覆盖成功，本地 APK 与手机 `base.apk` 哈希一致，冷启动成功。
- 中间强制构建与最后增量打包之间的 APK 字节差异已收窄为 3 个 Chaquopy 资产：`requirements-common.imy`、`app.imy`、`build.json`。最终验收只引用上述最终哈希；Release 前须另行解决可重复打包。
- 覆盖安装后再次实测搜狗键盘：开启键盘时整排操作按钮完整位于键盘上方，关闭键盘后恢复原首页坐标，无灰色遮挡层或多余空白。
- 本轮没有重跑三平台真实网络下载；YouTube、抖音、TikTok 的真实成品与吞吐报告仍以已有里程碑证据为准，不冒充本轮新验证。
- 本轮只将新经验增量沉淀到 `docs/verification/2026-07-21-keyboard-final-regression-experience-audit.md`；已完成的三平台下载、六屏 UI、图标和登录经验未重写。共享规则已增量更新至用户级工作台 UI 与产品交付 Skill，不复制进项目仓库。

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

- `HistoryFilters` 只保留平台与时间两个紧凑菜单按钮，外屏与内屏均固定同排。
- 默认显示“全部平台 / 全部时间”；点击后再展示完整选项，不让所有筛选项常驻占用时间线高度。
- Compose 仪器测试验证两个筛选器中心纵坐标一致、时间菜单可打开；OPPO 外屏实测同排且时间线首屏可见五条记录。

### OPPO Find N5 真机落地

- Debug APK 已使用覆盖安装方式落到 OPPO `PKH120`，应用数据未清除：`firstInstallTime` 保持 `2026-07-16 18:55:48`，本轮 `lastUpdateTime` 为 `2026-07-16 19:12:22`。
- OPPO Android 16/API 36 arm64 上的完整仪器测试通过：31 次执行、0 失败；3 项仅因 TikTok 外部实时条件跳过。
- Find N5 外屏 1140×2616、展开内屏 2248×2480、折回外屏均已真机检查；进程和当前页面在折叠状态切换期间保持连续。
- Android 16 不允许音频写入 `Movies` 的真机问题已修复：视频继续保存到 `Movies/南烛枫视频下载器`，MP3 改存 `Music/南烛枫视频下载器`，并新增真实 MediaStore 仪器回归。
- YouTube 公开样本 `Me at the zoo` 已在 OPPO 上完成读取、下载、M4A→MP3 转码和系统媒体库发布；历史记录为“完成”，文件 448.2 KB。
- 从 OPPO 媒体库读回的成品经 `ffprobe` 确认为真实 MP3：19.121583 秒、192 kbps、44.1 kHz、双声道；重复提交相同链接与“仅音频”规格后进入“已跳过”，未重复下载。
- 强制停止并重新启动应用后，“完成”和“已跳过”两条历史仍在，证明结果持久化正常。
- TikTok 官方公开示例已真机成功读取标题、作者并进入队列；抖音公开链接已进入真实解析链路，但当前外部条件明确要求 fresh cookies，未伪报成功。

### 六张独立定尺寸页面 UI（2026-07-17）

- 用户提供的两张三页拼板只作为视觉预览，不再被解释为多页同屏实现；正式运行时始终一次只显示一个页面。
- 外屏根视口统一为 1140×2616：首页、历史、设置分别独立渲染，统一使用底部导航，超出首屏的内容在页面内部纵向滚动。
- 内屏根视口统一为 2248×2480：首页、历史、设置分别独立渲染，统一使用左侧导航和相同安全区；首页为主队列加右侧任务栏，历史为双列时间线，设置为 2×2 语义卡片。
- 首页、历史、设置已统一为更深的低饱和灰绿页面底色和纯白 `#FFFFFF` 卡片；绿、紫、土色和橙色只用于标题、图标、状态与选中反馈。
- 三页导航已禁用 NavHost 淡入淡出和滑动过渡，页面内容即时切换；局部选中态与进度只使用 120–140ms 快速 ease-out 反馈。
- 设置页“同时下载”、“文件命名”、“下载路径”、“导出 cookies.txt”均已接通真实功能，不再是静态陈列。
- 历史固定只显示完成记录，保留平台与时间筛选；完成卡只显示核心信息，打开、复制、分享和删除继续收在右侧三点菜单。
- YouTube、抖音与 TikTok 已改用真实平台图形资源；Simple Icons 来源与许可说明在 `android/app/src/main/assets/licenses/simple-icons.txt`。
- 图标适配继续冻结为已确认的 16dp 留白版本；本轮未放大、缩小、裁切或更换图标适配逻辑。
- 六组参考/实现并排证据保存在 `.artifacts/ui-contract-2026-07-17/final-pairs/`；正式 QA 记录见根目录 `design-qa.md`，结论为 `passed`。
- 最终门禁：JVM 单元测试、lint、Debug APK 构建均通过；模拟器全套 44 项仪器测试完成，0 失败，3 项仅因外部 TikTok 实时条件跳过。
- 2026-07-17 最新 UI 已按“推送 APK → `pm install -r --user 0`”无损覆盖到 OPPO；模拟器 `NanzhufengFindN5Api35` 继续保持打开。

### 真实下载核心与永久吞吐报告（2026-07-17）

- Range 能力不再靠响应头猜测：每条真实直链先请求 `bytes=0-0`，只有精确返回 `206` 和匹配的 `Content-Range` 才允许分片；分片响应区间不匹配会清理分片并明确回退单连接。
- 平台策略分别为 YouTube 最多 6 连接/8 MiB 门槛、TikTok 最多 4 连接/8 MiB 门槛、抖音最多 3 连接/16 MiB 门槛；视频与独立音频流并行时，活动任务显示总连接数。
- 401/403/404/410/416、连接类传输失败或流提前结束在内部重试耗尽后，会重新调用媒体解析取得新签名直链一次；新旧分片计划指纹一致时继续断点，指纹变化时才清理旧半成品。
- Room 数据库升级到 v4，永久保存每条真实流的连接模式、Range 结论、网络/成品字节、耗时、平均/峰值速度、连接内重试、重新探测、回退原因与错误；3→4 迁移保留原任务和历史。
- 首页活动任务明确显示“正在探测连接能力 / 单连接 / 多连接 ×N”；历史卡直接显示连接模式和平均/峰值，三点菜单可打开每条真实流的完整吞吐报告。
- “智能读取”旁已增加“清空”按钮；OPPO 真机用原有 110 字抖音分享文本验证，点击后输入框立即为空。草稿只在页面首次建立时恢复，避免异步保存把已清空的旧链接重新回填；模拟器对应 12 项整类 UI 回归为 12/12、0 失败。
- 外屏首页已进一步改为队列优先：移除“队列 / 等待网络 / 已跳过”标签行，将全选并入标题区，把队列可视上限从约 2 条提高到约 4–5 条；8 条任务时显示绿色竖向滚动位置条。添加任务的平台说明改为输入框占位文字，成功加入提示不再额外占据底部空间。
- 首页标题区重复的“网络良好”已删除，历史页无实际行为的齿轮“管理”入口已删除；每条队列任务右侧均有独立删除按钮，活动任务会先安全停止再删除，等待任务直接删除。
- 失败任务现在保留在队列原位，以固定行高显示错误摘要、“重试”和删除入口；重试复用原任务 ID 与排序，不会跳成新行。结束通知有失败时会明确提示失败数量和查看/重试路径。
- 历史页已删除“只展示已完成的下载记录”及独占行的“平台 / 时间”标题，筛选芯片保留；设置页标题下的说明已删除，为实际内容腾出首屏空间。
- YouTube `youtube.com/live/<id>` 已归类为单作品链接，不再在智能读取阶段误报“不支持”；长回放传输遇到 `unexpected end of stream` 时会被识别为可恢复网络中断。
- 长视频 Range 分片现在允许未受影响的相邻分片继续完成；单流连接内最多续传 6 次。重新解析出新签名直链后，只有总字节和分片数指纹一致才复用已下载分片，指纹变化则清理旧分片，兼顾续传与内容完整性。
- OPPO 真机的 YouTube `/live/Z98F3gyNFqM`（已结束直播回放，时长约 3 小时 23 分）已完成视频、音频合并并发布到系统媒体库：`2026-07-16 “全哥价值投资”正在直播！.mp4`，成品 1,628,377,415 B，任务状态为 `COMPLETED`。此前失败尝试继续以 `FAILED` 保留在下载队列，错误摘要、重试和删除入口均可见；未完成任务不会再从队列消失。
- 这条 `/live/` 的最后一个音频分片为验证恢复链路而做过人工字节补齐，因此该任务的吞吐报告正确显示为 `--/s`，不能作为下载速度证据。当前 Clash/VPN 路径对同一 GoogleVideo CDN 曾从数 MiB/s 抖落到几十 KiB/s；既有三平台真机报告仍有效，但不能据此承诺所有直播回放稳定达到十几 MiB/s。
- OPPO 三平台真实成品与报告已完成，并在强制停止/重启 App 后仍可读取：
  - YouTube `Big Buck Bunny 60fps 4K`：视频 712,445,280 B，6 连接，平均 12,411,290 B/s、峰值 19,057,404 B/s；音频 30,767,611 B，6 连接，平均 11,851,930 B/s、峰值 45,135,131 B/s；合并成品 743,053,220 B。
  - 抖音《新闻播报》：15,816,197 B，单连接（低于 16 MiB 门槛），平均 2,778,183 B/s、峰值 5,459,210 B/s。
  - TikTok `Thank you, please come again`：7,106,139 B，单连接（低于 8 MiB 门槛），平均 2,938,849 B/s、峰值 29,616,130 B/s。
- 三个成品均由 OPPO MediaStore 读回为 `video/mp4`，分别位于 YouTube、抖音、TikTok 平台目录；连接内重试和重新探测均为 0，报告中的网络字节与已提交流字节一致。
- 真机证据：`/tmp/nanzhufeng-oppo-throughput-history.png`、`/tmp/nanzhufeng-oppo-throughput-report-dialog.png`；最终 APK 74,173,334 B，SHA-256 `47c606d2599db03922b73048463d0c2dd896f5ea2b14b0af0dfc0c1f865625bd`。
- 本轮失败保留与 `/live/` 收口证据：`/tmp/nzf-failed-queue.png`、`/tmp/nzf-live-after-completion-home.png`、`/tmp/nzf-live-completed-history.png`、`/tmp/nzf-settings-trimmed.png`。最终门禁为 102 项 JVM、15 项三页模拟器界面测试、13 项 Python 解析测试，全部 0 失败；`lintDebug` 与 `assembleDebug` 通过。

## 当前验证证据

### 三页折叠屏视觉优化（2026-07-16）

- 首页、历史、设置已统一为薄荷绿工作台：共享字阶、圆角、卡片语义色和明显的实心选中态均由 `core/ui` 统一拥有。
- 历史读取投影固定为完成记录；状态筛选行已移除，平台/时间筛选仍保留；完成卡的打开、复制、分享、删除均收纳到三点菜单。
- 设置已固定 YouTube 首位，抖音与 TikTok 连续位于“短视频平台”分组；内屏使用账号全宽、其余模块双列的自适应网格。
- 视觉回归的 JVM、lint 与 debug APK 构建门禁均通过：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
- OPPO Find N5 后台覆盖安装已改用 `adb push` 后的 `adb shell pm install -r --user 0`：`2026-07-16 22:57:13` 成功，无图形安装器确认页；不要再使用会触发 ColorOS 确认页的 `adb install`。
- OPPO 外屏 1140×2616 已独立捕获首页、历史、设置；内屏 2248×2480 已独立捕获同三页；展开后折回外屏仍停留在历史路由。截图均在 `.artifacts/ui-audit-2026-07-16/`，该目录不入 Git。
- 已在模拟器中覆盖历史筛选/更多菜单、内外屏导航、首页空状态和设置账号顺序/网格；OPPO 已真实展示三平台完成记录、连接模式、平均/峰值速度和吞吐报告弹窗，并在强制停止/重启后继续保留。

- JVM：`testDebugUnitTest` 通过，共 91 项测试、0 失败。
- 静态检查：`lintDebug` 通过，无阻断问题。
- 构建：本轮最终重新执行 `testDebugUnitTest + lintDebug + assembleDebug`，`BUILD SUCCESSFUL in 14s`。
- 模拟器全套仪器测试：`NanzhufengFindN5Api35`、Android 15/API 35、arm64，XML 记录 28 项、0 失败、3 项跳过；跳过项均为需要外部实时 TikTok 条件的 `TikTokLiveProbeInstrumentedTest`。
- MP3 定向验证：2 项 LAME PCM 编码、2 项 M4A→MP3/取消清理、1 项 `DirectMediaTransfer` 主链路端到端测试均通过；转码测试连续运行两次通过。
- 模拟器 UI：冷启动、通知权限、首页、历史、设置均可到达；应用进程定向错误日志为 0 行。
- MediaStore 定向验证：新增 1 项真实 MP3 写入 `Music`、内容校验和清理测试，Android 15 模拟器通过。
- 当前应用显示名统一为“南枫下载”；Gradle 构建直接输出 `android/app/build/outputs/apk/debug/南枫下载.apk`，不再生成或交付 `app-debug.apk`。
- Debug APK：`android/app/build/outputs/apk/debug/南枫下载.apk`，68,046,080 B，SHA-256 `01b01b2386934b82a12b87e3c4f3466a786c6426b20f0ebf48a4d411d43659df`。
- APK 完整性：`unzip -t` 返回无错误；两套 ABI 均包含 LAME 与 JNI 共享库。
- OPPO 已于 2026-07-21 通过 v3 证书谱系从旧 Debug 版无损升级到正式签名 `1.0.0 / versionCode 10000`；2026-07-26 又以同一正式证书无损覆盖到 `1.1.0 / versionCode 10100`，历史和输入草稿保留。

## 正式 Release 后仍待验证

1. 作者/频道/播放列表的大批量分页、过滤与取消勾选仍需更长时间的公开内容回归。
2. 断网恢复、用户暂停不自动恢复和完成通知已有自动化覆盖，但尚未在本轮 OPPO 用户界面逐项人工触发。
3. OPPO `v1.1.0` 同签名覆盖、哔哩哔哩与小红书真实闭环、重复去重及重启持久性均已完成；后续版本继续禁止卸载或清数据。
4. 正式签名密钥仍需用户控制的异地加密备份；本机密钥与钥匙串不能替代灾难恢复副本。

## 后续待办

下一阶段如需公开交付，应将当前 checkpoint 推送 GitHub，并按正式 Release 流程上传 `v1.1.0` APK、AAB 与 SHA-256 校验文件；本轮没有擅自推送或创建 Release。

执行依据：

- `docs/superpowers/specs/2026-07-16-android-true-mp3-encoding-design.md`
- `docs/superpowers/plans/2026-07-16-android-true-mp3-encoding-plan.md`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DirectMediaTransfer.kt`
- `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3AudioTranscoder.kt`
