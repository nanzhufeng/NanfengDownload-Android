# 南枫下载 Android 键盘与最终回归增量经验审计

## 1. 审计结论

- 第一用户任务：在外屏粘贴链接后，不受软键盘遮挡地直接使用“智能读取 / 清空”整排操作。
- 当前完成定义：关闭键盘时不改变已确认首页布局；开启键盘时只上移到整排按钮完整可达；关闭后恢复原坐标。
- 本次沉淀的核心判断：用真实 IME 几何和操作组实际坐标计算临时位移；最终产物必须在最后一次打包后固定哈希，并与目标设备安装包进行字节级对照。

## 2. 最终基准与明确排除

| 证据层 | 最终基准 |
|---|---|
| 源码提交 | 代码 checkpoint `5949206` |
| 正式构建 / 哈希 | Debug 验收包 `南枫下载.apk`，68,046,080 B，SHA-256 `01b01b2386934b82a12b87e3c4f3466a786c6426b20f0ebf48a4d411d43659df` |
| 自动门禁 | JVM 127 项、0 失败；Lint 通过；Debug 双 ABI 构建通过 |
| 模拟器 / 集成 | `emulator-5580`，1140×2616 / 442dpi / font scale 1.0；仪器测试 58 项、0 失败 |
| 真实设备 / 服务 | OPPO Find N5 同签名覆盖、冷启动、搜狗键盘开合验收通过；本轮未重跑三平台网络下载 |
| 数据保留 | 使用“推送 APK → `pm install -r --user 0`”；未卸载、未清数据；手机 `base.apk` 与本地哈希一致 |

明确排除：

- 废弃稿：早期三页拼图、不同高度的内屏预览和已被用户否定的布局。
- 被否定设计：键盘关闭时也整页上移，或通过全页 `imePadding` 制造灰绿色空白。
- 旧统计 / 旧构建：最后一次打包之前的 APK 大小、哈希和测试数不用于当前交付。
- 不可信环境：未锁定序列号的 `connected*` 测试、估算坐标的真机点击和未显示真实键盘的截图。

## 3. 分类口径

- A 项目专属事实：包名、设备序列号、当前 APK 哈希、测试数和代码 checkpoint。
- B 跨项目可复用规则：键盘几何适配、已确认布局冻结、测量容差、最终产物哈希锁定和 checkpoint 范围审计。
- C 用户明确长期偏好：键盘打开仅移动到必要高度，关闭后不改变原布局；已确认的部分不在后续沉淀中重做。
- D 平台 / 设备约束：Android Compose IME 几何、OPPO 安装器、折叠外屏视口和 OEM 输入法。
- E 尚未验证推测：Chaquopy Debug 资产的非稳定压缩是否影响 Release 可重复构建。

## 4. 反馈—原因—实现—验证证据矩阵

| 维度 | 用户反馈 / 原问题 | 首个语义分叉 / 根因 | 最终实现 | 自动测试 | 模拟器 / 集成 | 真实设备 / 服务 | 尚存风险 | 分类 |
|---|---|---|---|---|---|---|---|---|
| UI 与交互 | 关闭键盘时不得改原布局；开启时按钮刚好露出 | 把键盘适配误做成全页滚动或重复 IME 留白 | 读取 IME 高度与操作组 `boundsInRoot`，仅对紧凑页施加临时位移，IME 消失即归零 | 资源合同锁定固定 `Column`、列表 `weight(1f)`、真实 IME 读取和禁止 `imePadding` | 完整 58 项仪器回归 | OPPO 搜狗键盘打开/关闭，用户确认正确 | 其他 OEM 键盘高度与多窗口状态未全覆盖 | B / D |
| 测试与验收 | 最终回归必须反映真实最终页面 | 旧设置页测试用无规格来源的 28% 硬阈值，两张等高卡片均因 28.36% 失败 | 先输出实际高度与比例，再将共享容差校正为 29%；不改已确认生产 UI | 定向红灯、定向绿灯、完整 58 项绿灯 | 1140×2616 / 442dpi / font scale 1.0 | 真机视觉无变化 | 容差不能被继续放宽为无效测试 | B |
| 交付 | 最终文档、APK 和手机必须是同一份成果 | 在记录哈希后又执行打包任务，Chaquopy 资产重打包导致 APK 字节改变 | 最后一次门禁后锁定产物，再安装、拉回 `base.apk`、对比哈希；之后不再打包 | APK 签名、双 ABI、构建和哈希检查 | 模拟器仅承载测试包 | OPPO 本地/设备 APK 哈希一致，冷启动成功 | Release 可重复打包尚未建立 | A / B / E |
| 失败经验 | 已确认的界面不应在“优化”中被再次改变 | 把通用键盘滚动方案误套到固定工作台，忽略了关闭态也是合同 | 用户确认“对了”后冻结两种状态几何，后续只做验证与文档 | 关闭态和开启态分别建立契约 | 专用模拟器隔离 | 真机再验收 | 用户改变设计合同时才能解冻 | B / C |

## 5. 跨项目规则

1. 固定工作台遇到键盘遮挡 → 保留关闭态布局，以真实 IME 几何和完整操作组实际坐标计算最小位移 → 分别测量键盘开/关坐标并用真机 OEM 键盘验收 → 禁止默认整页滚动、重复 `imePadding` 或永久上移。
2. 已确认视觉与交互 → 用户确认后冻结为回归合同 → 只修真实红灯且对比确认状态 → 禁止为追求“更紧凑”、“更通用”或让旧硬阈值通过而改生产 UI。
3. 布局契约受几何阈值约束 → 断言失败时先输出实际值、基准值和比例 → 容差必须来自已确认设计与可解释的测量波动 → 禁止不经测量直接放宽或为迎合测试压缩界面。
4. 生成最终安装包 → 在最后一次会改变产物的任务后记录大小和哈希，安装后从设备拉回对比 → 哈希一致后才写入交接 → 禁止记录哈希后再执行打包、重签名或产物重命名。
5. 建立本地 checkpoint → 先核对 Git 根、分支、改动规模和目录分布，只显式 stage 当前任务文件 → 执行 staged diff 检查与门禁 → 禁止通过 reset、clean、stash 或吸收无关改动来制造干净状态。

## 6. 项目专属事实与遗留风险

### 已确认事实

- 项目事实入口为 [context](../../context.md)，动态验证入口为 [PROJECT_HANDOFF](../../PROJECT_HANDOFF.md)。
- 键盘生产实现由 [FormalHomeSections.kt](../../android/app/src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt) 拥有；仪器容差位于 [SettingsScreenInstrumentedTest.kt](../../android/app/src/androidTest/java/com/nanzhufeng/videodownloader/feature/settings/SettingsScreenInstrumentedTest.kt)。
- 当前验收 APK 仍是 `0.1.0-probe` Debug 版，不是正式 Release。

### 平台 / 设备约束

- Android 15/16 edge-to-edge 与 OEM 输入法不能只依赖 `adjustResize`；Compose 必须读取 IME inset 并用真实坐标验证。
- OPPO 覆盖安装固定使用 `pm install -r --user 0`，签名不一致时停止。

### 尚未验证与遗留风险

- YouTube、抖音、TikTok 真实网络下载本轮未重跑，沿用已有里程碑证据。
- Release 版本号、发布签名、AAB 和可重复打包门禁未完成。
- Chaquopy Debug 资产在强制与增量打包间会发生字节级差异，当前只能作为 Release 前的可重复性风险，不能推断根因已解决。

## 7. 失败经验与防回归资产

| 被否定做法 | 失败原因 | 今后禁止项 | 机器防回归 |
|---|---|---|---|
| 把首页换成整页可滚动容器 | 键盘关闭时也改变已确认布局 | 未经设计合同变更不得改关闭态结构 | `HomeLayoutResourceTest` 锁定 `Column` 和列表 `weight(1f)` |
| 全页叠加 `imePadding` | 产生灰绿色空白与过度上移 | 同一层不得同时依赖系统压缩与手工 IME 留白 | 资源测试显式禁止 `imePadding` |
| 用估算坐标点真机 | 曾误打开历史详情，截图不能证明键盘路径 | 优先从 UI 节点读取可点击坐标 | 用 `uiautomator dump` 校验焦点和操作组 bounds |
| 用无来源 28% 阈值反向改 UI | 设计已通过，差值只有 3.4dp | 必须先输出实际几何再判断产品或测试错误 | 断言失败消息永久带真实比例 |
| 记录哈希后再打包 | 最终 APK 字节已变化，文档与手机不再对应 | 哈希锁定后禁止再运行产物生成任务 | 安装后拉回 `base.apk` 与本地做 SHA-256 对比 |

## 8. 产物清单

- context：[context.md](../../context.md)
- 架构所有权：延用现有分层和唯一入口，本轮未改变业务所有权。
- 交接 / 决策：[PROJECT_HANDOFF.md](../../PROJECT_HANDOFF.md)
- 共享 Skill / SOP：`design-apps-with-nanfeng-workbench-ui/references/implementation-sop.md`、`qa-matrix.md`；`develop-apps-with-nanfeng-product-standards/references/delivery-and-acceptance.md`。
- 模板：延用 `app-experience-audit-template.md`，本轮不建重复模板。
- 自动检查：`HomeLayoutResourceTest`、`SettingsScreenInstrumentedTest`、`validate_app_experience_distillation.py`、Skill `quick_validate.py`。
- 长期记忆更新：只记录“已完成部分不重做、最终产物必须在最后打包后锁定并与设备哈希对照”的跨项目偏好，不写入项目哈希或设备序列号。

## 9. 验证等级

| 等级 | 命令 / 环境 | 结果 | 不能替代的更高等级 |
|---|---|---|---|
| 文档与 Skill | 经验审计校验、Skill `quick_validate.py`、链接/占位符/秘密扫描 | 审计与 context 校验通过，6 个本地链接有效；两个现有 Skill 均通过 `quick_validate.py` | 不替代 App 测试 |
| 自动测试 | `:app:testDebugUnitTest` 与仪器测试 | JVM 127 项、0 失败；仪器 58 项、0 失败 | 不替代 OPPO 输入法 |
| 构建 | `:app:lintDebug :app:assembleDebug` | Lint 与双 ABI Debug APK 通过 | 不替代 Release 构建 |
| 模拟器 / 集成 | `emulator-5580` | 58 项、0 失败 | 不替代 OEM 输入法 |
| 真实设备 / 服务 | OPPO Find N5 外屏 + 搜狗键盘 + 同签名覆盖 | 开/关布局、数据保留边界、APK 哈希一致和冷启动通过 | 不替代本轮未重跑的三平台网络下载 |
