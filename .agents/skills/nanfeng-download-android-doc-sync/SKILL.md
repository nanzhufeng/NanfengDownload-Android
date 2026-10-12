---
name: nanfeng-download-android-doc-sync
description: Synchronize 南枫下载 Android handoff, verification, architecture, and reusable-experience documents from current code, Git, artifacts, and device evidence. Use after a verified increment, checkpoint, release, or when documentation conflicts with the repository.
---

# 南枫下载 Android 文档同步

## 文档所有权

完整开发档案放稳定架构、决策、Git演进和踩坑依据；可迁移经验区分通用/项目/不可照搬；AGENTS只放长期硬规则；五类Skill各有触发、执行、产物和边界。PROJECT_STATUS放当前能力/基线/缺口，PROJECT_HANDOFF放任务接续，日期verification放实际证据。

## 执行

1. 盘点Git根、分支/HEAD、工作树/未跟踪、可达历史；现场读Gradle版本/依赖/签名实现、Room/schema、所有者、测试与适用产物/设备记录，不信任旧摘要。
2. 常规增量只读受影响合同/记录；全项目复盘索引全部代码、文档、配置、测试与Git历史，按Android主线、旧桌面、第三方和历史资产分层。说明全量扫描/定向审阅与未运行路径，不声称逐行证明或全功能重测。
3. 搜索已有入口并增量更新，不另建竞争档案/Skill。保持[完整开发档案](../../../docs/南枫下载 Android 完整开发档案.md)与[可迁移经验](../../../docs/可迁移开发经验.md)唯一正文；旧记录保日期/版本，必要时加历史快照路由。
4. 每项结论附实际代码、Git提交或验证记录。冲突表列旧声明、当前依据、处理；区分源码能力、执行要求、实际门禁、测试结果、设备/服务和未验证推测。
5. 冻结产物时用tools/verify_frozen_release.py核对现有APK、release元数据与生产提交，只证明源/字节/包内容；不重建。若仅文档变化，保留适用旧构建/设备证据及原运行时间。
6. 复盘输出至少含目标/架构/技术栈/目录/核心模块/数据流/决策原因/历史/踩坑/验证/部署/已知问题/路线；不把未验证内容填成事实，永久规则不夹带临时设备、版本和统计。
7. 无用户凭据、私有媒体/数据库/路径进入永久文档；模板不代替用户对长期记忆、主设备、推送或发布的授权。全盘复盘若明确不改业务代码，结束前按摘要证明生产/测试/配置未动。
8. 校验本地链接/路径、结构、秘密模式/未完成占位、Skill frontmatter/UI元数据、diff空白与既有WIP保留。quick_validate缺环境依赖时按标准库审计兜底并注明；不要污染全局解释器或清用户缓存。
9. 阶段切换运行全局context_gate，结束增量维护状态/交接，列新改文件与未确认项；用户未要求提交/推送时保工作树供审阅，不擅自做远端动作。

## 完成

结论须能回溯到当前范围和具体证据。文档完成不能冒充设备/服务验收；交接只引用详细证据，不重复完整档案。

## 合同漂移门禁

先读[合同总入口](../../../docs/contracts/README.md)定位唯一正文；项目根运行 `python3 tools/check_contracts.py`。来源/合同有变化时先语义审阅和对应验证，再更新registry的已审阅摘要，禁止仅重算摘要越过审查。检查通过不替代功能/构建/设备结果；旧方案/交接只作历史参考。
