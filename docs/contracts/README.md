# 南枫下载现行合同总入口

2026-10-18完成全项目合同复核；2026-10-19按本地1.2.157候选增量更新历史清理、可读性、三页共享表现、设置菜单与交付来源；[本轮审计](../verification/2026-10-18-contract-consistency-audit.md)与[首次统一记录](../verification/2026-10-12-contract-consistency-audit.md)分别保留范围，不刷新旧业务验收日期。本入口只路由，不复制各合同正文。

## 权威层级与冲突处理

用户最新指令与安全规则优先。实际行为以当前源码及适用验证为事实源；[AGENTS](../../AGENTS.md)保存执行约束，不将未实现门禁写成已实现。产品目标高于实现的部分明确列缺口，不能为了对齐源码降低目标或改写旧失败。

| 概念 | 唯一现行正文 | 主要所有者 |
| --- | --- | --- |
| 来源/读取/会话与凭据 | [来源合同](../source-discovery-and-session.md) | UrlClassifier、发现引擎、SessionAccessPolicy、目标捕获/提取 |
| 任务/设置/历史/输出生命周期 | [任务与输出合同](../task-history-and-output.md) | Repository、TaskTransitionPolicy、TaskRunner、OutputStore |
| 连接/续传/媒体处理/吞吐 | [共享传输合同](../download-transfer-policy.md) | TransferPolicy、HTTP/协议下载器、媒体处理器 |
| 播放位置/手势/反馈 | [播放合同](../video-playback.md) | 根播放会话、PlaybackMemory、GestureController |
| 收藏/筛选/迁移 | [收藏合同](../history-favorites.md) | HistoryScreen、Repository、Room历史行 |
| 后台进度/简洁详细模式 | [后台岛合同](../background-download-island.md) | Worker、DownloadIslandState、Overlay |
| App内短反馈/字体/动效 | [反馈岛合同](../design/2026-10-10-island-feedback-ui-contract.md) | IslandFeedback、IslandMotion、完成观察器 |
| 测试/签名/冻结/覆盖/发布 | [交付事实合同](../release-and-verification.md) | Gradle门禁、验证工具、执行安全规则 |

两类岛共享动效所有者，但窗口、权限、尺寸与字阶是不同场景的明确差异，不强行使用同一组尺寸。播放提示也不属于任务进度岛。

## 文档类别

- 上表正文是现行实现合同，含来源、公开入口、事实语义、验证门槛及目标差距。
- `superpowers/plans`、`superpowers/specs`是方案/演进记录；保留原目标，不自动认定已实现或已废弃。`archive`是历史交接，不能从其中旧参数恢复功能。
- `verification`是带版本、样本和日期的证据，不作为另一个规范所有者；Release清单固定旧包，不能改为最新工作树。
- 完整开发档案解释架构/决策，经验文档提炼原则；二者引用现行合同，出现旧概括时先核对实际所有者。状态/交接不重复维护参数表。旧ChatGPT/Claude说明与design-qa属于历史参考，不能作为新任务指令。
- 注册表的`documentRoles`登记入口、架构、经验、流程、状态、交接和历史参考；`evidenceDirectories`只分类日期证据。项目根、docs与.agents的Markdown须有归属，新增文档不能无分类漏过维护门禁。第三方许可证与App测试夹具说明不属于产品合同。
- 未来UI实现按当前用户要求读取`design-apps-with-nanfengji-ui/references/ui-foundations.md`；当前布局/设置是否达到新目标单独核验。历史取消功能审阅是旧实现事实，不覆盖最新用户对后续新功能的要求；本次不补UI。

## 变更与升级闭环

1. 从注册表找到唯一合同和代码所有者，列实际入口及受影响/不受影响范围。
2. 区分功能变更、文档纠错与证据更新；涉及新行为另建立行为回归，纯文档不改业务、不刷新旧运行时间。
3. 同一增量更新对应合同、代码/测试和必要状态；旧方案/失败证据保留层级与日期，不制造第二真相源。
4. 对来源、迁移、输出或媒体变化按适用等级验收后，更新注册表中的已审阅源码摘要，并同步`reviewedAt`、审阅基线与适用证据；禁止仅重新计算摘要掩盖语义未审阅。
5. 项目根运行 `python3 tools/check_contracts.py`。它检查现行与辅助文档覆盖/唯一性、路径/链接（含编码空格和README预览图）、递归历史标签和已审阅源码漂移；通过不证明合同语义、功能、构建或设备验收。

注册表为[registry.json](registry.json)，只维护文档/源码/证据路由和审阅摘要，不复制UI/传输参数。新增合同必须注册，受保护冻结包使用既有独立核对工具。

维护工具自身的回归入口见项目[testing Skill](../../.agents/skills/nanfeng-download-android-testing/SKILL.md)；它使用临时目录，不启动App或接触设备/数据。链接门禁检查本地目标存在与仓库边界，不验证网页可用性、Markdown标题锚点或功能语义。
