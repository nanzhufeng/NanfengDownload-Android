# Project Status

> 当前状态摘要；代码与适用验证证据优先。基线差异触发复核，不直接推翻全部结论。

## Verified

- Last verified: 2026-10-11 14:12 +0800；本轮单测/宿主回归、冻结源码/APK与文档实际复核；构建/设备旧证据运行时间不刷新。
- Verified branch: main
- Code baseline: d0602d13c87411f5225889c04ef9a6f403a2a362
- Verification scope: COMMIT；Android下载/发现、后台岛、播放记忆、收藏、Room11及对应测试；生产源码内容已在releases/v1.2.146/source-manifest.json冻结。该基线为本轮业务实现提交；后续只调整文档/清单，生产源逐文件哈希与此提交一致。
- Verified by: Codex
- Status: Active

## Current

- GitHub正式Release：[v1.2.146](https://github.com/nanzhufeng/NanfengDownload-Android/releases/tag/v1.2.146)，APK57,718,955B，远端SHA256与冻结包一致，非Draft/非Pre-release。

- 1.2.146/10346正式同签名包，Room11；历史收藏、播放记忆/左右10秒连续跳转、后台简洁/详细岛已在隔离模拟器正常UI验收，边界见对应验证报告。
- 已按用户授权覆盖OPPO142，安装包与正式冻结SHA一致，安装身份及15条媒体元数据保持；未主动启动App进行业务验证。
- 下载采用共享有界Range分块、续传与取消/输出校验；公网效果按各平台独立证据，不声明全平台固定速度。

## In Progress

- 本轮本地checkpoint/合同/经验增量已完成验证；无进行中的发布工作。v1.2.146既有正式交付不重复，见本次检查点记录。

## Blocked

- 无；测试解释器依赖已定位于当前Android构建的Python依赖目录，已通过Python120项与JS4项，无实际阻塞。

## Tests

| 检查 | 结论 | 时间 / 平台 / 范围 | 证据 |
| --- | --- | --- | --- |
| JVM Debug/Release | PASS | 2026-10-11/macOS JBR/146未变生产源码，本轮实际重跑各443，0失败/错误/跳过 | verification/2026-10-11-checkpoint-experience-increment.md |
| lint/Release/R8 | PASS | 2026-10-11/146，0错误/33警告/3信息 | 同上 |
| Python/JavaScript | PASS | 2026-10-11/146未变生产源码，本轮实际重跑Python120与JS4 | verification/2026-10-11-checkpoint-experience-increment.md |
| 冻结/正式沉淀 | PASS | 2026-10-11/479受控源、APK摘要、4个拒绝探针、经验审计/链接检查 | 同上；Skill quick_validate缺PyYAML，标准库审计兜底 |
| 隔离正常UI | PASS | 2026-10-11/emulator-5580/146，收藏/取消/冷重开/宽窄切换/字体130% | 同上 |
| OPPO正式覆盖 | PASS | 2026-10-11/140→142，同签名/包SHA/媒体元数据与安装身份 | verification/2026-10-11-oppo-release-142-overlay.md |
| OPPO业务/长期后台 | NOT RUN | 不主动操作主设备任务 | 覆盖不替代业务结果 |
| connected/仪器测试 | N/A | 永久禁止 | 项目AGENTS.md |

## Verification Gaps

- AVC之外TS与更多长片封装、窄屏多任务空间仍需复核。
- 所有平台每次首次读取≤1秒、所有下载5–10MB/s、OEM长期后台未全面通过，见既有网站/性能验收。
- OPPO私有Room迁移、收藏/播放用户路径未主动验证；真实历史删除仅宿主合成夹具验证。
- 正式APK构建于发布提交之前；479个生产文件已逐一核对与该基线相同，构建时间/签名/版本/包哈希单独记录。

## Sync & Safety

- Sync checked at: 2026-10-11 14:12 +0800；写入本状态前，当前HEAD与本地origin/main均为110ccb5e2a509f4d002b3ac2ef321d1480d4e8ad；本轮未刷新远端或附件。
- Code / docs sync: OK；本轮传输合同、TS封装所有者、历史冲突日期和正式经验增量与冻结生产源对齐；既有签名混配门禁缺口单列。
- Git working tree: DIRTY；写入时本轮文档/工具待本地提交，另有先前SDD报告删除；最终标记checkpoint/146-final-regression-20261011，原删除不纳入。浏览器/旧QA图保留且已忽略。
- Remote sync: LOCAL ONLY（本轮checkpoint）；146代码基线及原收尾文档已在origin/main。本轮文档/工具与两个checkpoint标记不推送，不改变v1.2.146标签或正式附件。
- Rollback point: UNKNOWN；未把当前底层HEAD默认视为业务稳定回滚点。
- Secrets/config risk: REVIEW NEEDED；本轮扫描无实际凭据，但既有签名按字段回退未自动拒绝来源混配；详见经验审计。原正式包身份证据有效，未改冻结源码。

## Next

1. 下次构建配置增量补签名整组取值/混配拒绝回归；不重建本轮冻结包。
2. 按新样本验证平台吞吐、更多封装格式及读取时延。
3. 用户明确授权覆盖或业务验收时再核对主设备签名/数据与目标路径。
