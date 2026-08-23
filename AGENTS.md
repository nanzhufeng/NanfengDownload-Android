# 南枫下载 Android 项目规则

## 事实源与范围

- 当前 Android 实现以 `android/`、`PROJECT_HANDOFF.md` 和 `docs/verification/` 为准；版本、数据库版本、依赖与产物路径必须现场读取 Gradle/源码，不信任旧 README 或历史计划。
- 根目录 `app/`、`start.py`、PySide 打包脚本是旧桌面原型，不得与 Android 业务链混改；要维护它时先单独确认范围。
- 任务必须保持“用户输入 → 发现 → Room 任务/历史 → Worker → 传输/处理 → MediaStore/SAF → 历史”的单一事实链。UI 不得直接写 DAO，任务状态只经 `DownloadRepository` 和 `TaskTransitionPolicy`。

## 数据、来源与外部平台

- 公开媒体、会话、下载任务、MediaStore 成品和历史状态是不同事实，不能用其中一个代替另一个。失败必须保留原因与可操作建议，不能假报完成。
- 第三方平台页面、短链、Cookie、CDN 和反爬均视为不稳定外部条件；新平台/路由必须限制官方域名、目标作品身份和来源作用域，不能以泛化 URL 兜底。
- 抖音 `/note/` 图集只能从目标作品的结构化 `urlList` 进入任务；数量必须精确匹配声明且排除 `tplv-dy-water`。该门禁是来源保证，不可称为逐张像素水印证明。
- 不把账号密码、Cookie、Token、签名密钥或用户媒体写入源码、文档、日志、测试夹具、提交或 Release。会话必须按站点与目标主机作用域处理。

## 数据迁移与输出

- 变更 Room entity、DAO 或持久字段时同步新增 schema、显式 migration 与迁移回归；不得破坏已有任务、历史或用户媒体。
- 下载、转码、合并、分段和发布必须先校验真实媒体；MediaStore/SAF 发布要可回滚并在失败时清理本次新建输出，不能覆盖已有用户成品。
- 改动版本、签名或正式包前先读取现有版本、签名、设备数据指纹。正式覆盖只允许同签名、递增版本的 `pm install -r --user 0`，且须有用户授权；绝不卸载、清数据、注入数据库或强装异签名包。

## 验证与交付

- 永久禁止运行任何 `connected*AndroidTest`，也不得通过序列号、环境变量或脚本绕过。不得向 OPPO 主设备部署 Debug/仪器包或自动操作用户任务。
- 自动单测、lint、隔离模拟器、OPPO 覆盖、OPPO 用户路径、外部平台实际结果必须分层报告；构建、APK、入队或 URL 门禁都不等于真实下载闭环。
- 正式签名从完整 `NANFENG_RELEASE_*` 环境变量读取，用户级 Gradle 专属配置只作备用；签名不全或不匹配立即停止。
- 交付前只暂存本次范围文件，检查差异、空白和敏感内容；未获授权不得推送、发布或改动远端状态。

## 协作入口

- 实现使用 `.agents/skills/nanfeng-download-android-development/`；排错、验证、审查和文档同步分别使用同级对应 Skill。
- 项目完整事实、历史冲突和路线见 `docs/南枫下载 Android 完整开发档案.md`；跨项目经验见 `docs/可迁移开发经验.md`。
