# 后台下载与灵动岛

2026-10-10。本轮用户要求依照《南枫系手机应用_灵动岛交互设计建议》增加后台下载进度并提高后台稳定性，明确要求参考南枫转写最新简洁/详细模式与切换；只修改下载器，不修改转写器或跨应用任务中心。

## 设计来源与模式合同

参考当前南枫转写0.14.7的`TaskIslandOverlay.kt`、`IslandDisplayController.kt`及`docs/background-island.md`，2026-10-10现场读取并再次核对哈希。用户提供的建议文档是视觉/行为输入，不能扩张为操作其他App或主设备的授权。

- 默认简洁版：一行图标、实际阶段、可计算百分比及下载速度。可见胶囊34dp、触控至少48dp、最小宽220dp、左右各24dp安全留白；宽度随内容增加并受视口约束。沿用转写器14sp/12sp字阶、深色圆角面、语义色，复用下载器已有Noto中文550/400字重及等宽数字，不增加字体资源或装饰图像。
- 点击简洁版展开详细版：展示当前任务标题、质量、下载量/总量、预计剩余或实际处理阶段，提供“查看下载”和“停止下载”。点击标题区收起；进度更新保持用户当前模式，不自动展开。
- 新任务或返回软件后重新退到后台，恢复简洁版。锁屏、屏幕非交互或本App有可见Activity时隐藏；使用Activity生命周期回调，不以FGS/Room绑定的进程importance猜测界面可见。
- 无明确总量时不编造百分比；解析、下载、合并、转换、分段、校验、保存分别呈现。网络字节传完/阶段100%不能表示已完成，未结束最大99%。完成提示只由已校验、发布和归档后的真实队列结果生成，失败/跳过/网络等待分别表达。
- 同一真实`DownloadIslandState`驱动进度通知与悬浮窗，进度限频到每秒，任务/阶段/终态立即更新。终态短暂停留后移除，旧Worker的窗口不能覆盖新Worker的窗口。

## 入口与唯一所有者

| 入口 | 所有者/职责 | 验证 |
| --- | --- | --- |
| 正常首页开始、暂停、停止 | 既有DownloadEngine→WorkManager→DownloadTaskRunner→Repository | 继续既有队列与缓存，不能从Window直接写DAO |
| 后台实时状态 | Worker读取Repository的队列→DownloadIslandState | 真实字节、速度、阶段；不读取旧失败项冒充当前下载 |
| 简洁/详细切换 | DownloadIslandDisplayController→DownloadIslandOverlay | 默认简洁、点击切换、更新不改模式、新任务/回到App重置 |
| 查看下载/进度通知点击 | 正常MainActivity导航请求→首页 | 即使原来在设置/历史也回下载页；不在测试中用手动Intent代替点击 |
| 停止下载 | DownloadIslandOverlay→既有DownloadEngine.stop | 本任务取消、无假成品；其他任务仍走原恢复规则 |
| 设置 | 下载规则“后台下载与灵动岛” | 用户管理悬浮窗/通知/电池策略，不自动加白名单；关于同步功能审阅 |
| 旧任务/历史/输出/身份/读取/下载器传输 | 原领域与数据所有者 | 本轮不改Room10/entity/迁移，不重新下载已通过样本 |

## 后台机制与权限

保留唯一WorkManager队列及前台dataSync Worker，使用WorkManager管理的前台服务唤醒锁与取消/恢复机制，不另建并行下载服务。原2.9.1缺少Android15短服务/dataSync超时停止修复，升级至兼容SDK35的2.10.5，系统前台时限到达时由WorkManager停止Worker，保留断点并走原中断恢复，不能循环绕过时限。窗口或通知更新失败不会取消已启动的下载；首次前台提升失败仍由系统/WorkManager处理，不能隐藏违规后台运行。

悬浮窗需要用户在系统页面授权；未授权保持下载通知，不阻塞实际下载。 明确点击开始下载时，首次未授权会显示一次非阻塞“开启后台灵动岛”引导，下载先按正常流程启动；用户可开启或稍后。自动恢复不弹首次引导，已拒绝/撤销后从设置同一入口补授予，不自动修改系统权限。锁屏采用PRIVATE通知并隐藏悬浮窗，实际通知可见内容服从系统设置。应用悬浮窗不等于厂商原生灵动岛/白名单。

Android15前台dataSync存在每日累计预算，Android16长Worker还受作业配额；系统强停、关机、厂商限制仍可能中断，不能保证“永不被杀”。重新启动继续已有恢复链。官方来源：[WorkManager2.10超时修复](https://developer.android.com/jetpack/androidx/releases/work#2.10.0-alpha03)、[长Worker](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)、[FGS时限](https://developer.android.com/develop/background-work/services/fgs/timeout)。

## 本轮验证边界

129模式/后台实测与最终正式包130、JVM416/416、隔离正常UI模式/后台/熄屏/取消/成品证据记录于`docs/verification/2026-10-10-background-download-island.md`。只允许emulator-5580；不操作转写器5592、OPPO或其他模拟器，无connected Android测试、数据库注入、清数据、缓存清理、推送或发布。OEM真机、真正6小时系统时限、Android16配额和厂商原生岛仍需独立验收。

132共用低速恢复及131首次授权/模式、132正常UI成品/冷历史验证见`verification/2026-10-10-shared-slow-download-recovery-and-island-permission.md`。OPPO132后续保数据覆盖见`verification/2026-10-10-oppo-release-132-overlay.md`，主设备灵动岛用户模式仍未新验收。

## 2026-10-10 统一切换动效

简洁/详细模式沿顶部中心锚点做尺寸缩放，360毫秒轻微回弹；系统窗口动画关闭，不横向滑动。水平定宽的透明承载窗保持中心，切换期间纵向覆盖旧/新尺寸，收缩不裁切旧胶囊；快速重复点击取消旧动画并从当前尺寸继续，旧回调不能改新窗口。系统关闭动画时立即进入最终状态。后台进度更新不重复播放切换动画；临时反馈也复用原位缩放。删除“进度以实际任务为准”等冗余小字，真实状态链不变。

详细版操作为108dp宽、34dp可见高度的胶囊，实际触控48dp，12sp字阶及语义色细描边；停止仍调用原DownloadEngine。
