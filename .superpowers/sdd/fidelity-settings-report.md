# 设置页视觉还原纠偏报告

日期：2026-07-16
范围：仅设置页；未连接、安装或操作 OPPO 真机。

## 视觉基准与核对

- 已逐项读取 `2026-07-16-android-three-page-foldable-visual-refinement-design.md` 与纠偏合同，并以用户确认稿中设置页为唯一视觉方向。
- Visual Companion `http://localhost:55812/...` 当时拒绝连接；Browser skill 的必需本地 client 文件也不存在。因此未以任何替代页面猜测设计，保留确认稿与两份正式规格作为实现依据。

## 确认稿对照

| 确认稿要求 | 本次落点 | 验证口径 |
| --- | --- | --- |
| 内屏账号/质量首行、任务/存储第二行 | 移除账号卡的全宽 span；`LazyVerticalGrid(2)` 依次放置账号、质量、下载规则、文件存储四卡 | 仪器测试读取实际 root 像素边界，账号宽度不超过质量卡 1.3 倍（通过） |
| 外屏不可出现巨型账号/质量卡 | 平台行由 6dp 缩至 2dp，组间距 8dp 缩至 4dp，说明文字降为 bodySmall，操作按钮横向内边距收紧；质量单选图标为 32dp | 实际外屏 Compose 边界测试要求账号、质量各不超过根视图高度 28%，且质量卡起点不晚于根视图 45%（通过） |
| YouTube 首位，抖音/TikTok 连续同组 | 原有排序与短视频组保留，未触碰 Cookie、登录、清除回调 | 既有顺序测试通过 |
| 平台不能是相同绿色通用标识 | 保留现有可辨识 Material 图标，并分别使用 YouTube 红 `#FF0000`、抖音深黑 `#161823`、TikTok 青 `#00B5C8` | 共享 `PlatformIcon` 统一着色；不改变图标的内容描述 |
| 四个决策模块紧凑平铺 | 原独立“内容范围”卡的静态说明并入“文件存储”，防止生成第三行第五卡；不涉及任何业务状态或回调 | 展开网格仍能滚动到文件存储卡 |

## TDD 与验证

1. RED：先改仪器测试；旧实现中内屏账号宽度违反 1.3 倍上限，外屏没有可验证账号卡边界（新增紧凑卡合同因此失败）。
2. GREEN：只在 `ANDROID_SERIAL=emulator-5554` 运行 `SettingsScreenInstrumentedTest`，3/3 通过；本轮不安装、截图或操作 OPPO。
3. 已知环境：Android Studio JBR 需显式设置为 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`；构建存在既有 compileSdk/CMake 警告，不属于本次改动。
