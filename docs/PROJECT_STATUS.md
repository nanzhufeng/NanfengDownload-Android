# Project Status

> 当前状态摘要；代码与适用验证证据优先。基线差异触发复核，不直接推翻全部结论。

## Verified

- 2026-10-13最新147 WORKTREE：Release468项、lint0错误/43警告/3信息、R8及正式构建通过（lenient）；OPPO142→147同签名覆盖和回拉字节核对通过，安装身份与15条媒体投影不变。未自动启动/业务验收。[本次证据](verification/2026-10-13-oppo-release-147-overlay.md)。以下旧日期记录只代表其当时范围。

- 2026-10-13最新WORKTREE审查：输入/发现代次与两个捕获Activity请求归属已修复；底层HEAD819606b，全量Debug467项、随后补充首页25项PASS（lenient）。[范围/原故障缺口](verification/2026-10-13-input-discovery-architecture-audit.md)。旧COMMIT证据不代表新代码设备通过。

- 2026-10-13本次增量：WORKTREE（底层HEAD819606b76de4a9c163147fe52599a806e1587616），首页粘贴替换与24项定向回归/Debug编译通过，使用依赖校验lenient；默认严格依赖校验失败，设备未验。证据：[输入验证](verification/2026-10-13-link-paste-replacement.md)。下列146COMMIT证据保留原日期，当前工作树输入适配已不同于冻结源。

- Last verified: 2026-10-12 12:30 +0800；现行合同/源码所有者、漂移门禁、冻结源/包及WIP保全核对；业务测试/设备沿用原日期证据。
- Verified branch: main
- Code baseline: d0602d13c87411f5225889c04ef9a6f403a2a362
- Verification scope: COMMIT；Android下载/发现、后台岛、播放记忆、收藏、Room11及对应测试；生产源码内容已在releases/v1.2.146/source-manifest.json冻结。该基线为本轮业务实现提交；后续只调整文档/清单，生产源逐文件哈希与此提交一致。
- Verified by: Codex
- Status: Active

## Current

- 2026-10-13发布准备：148源码与精确APK冻结一致，隔离5580设置/历史正常UI与README预览已更新；用户授权本次提交main并正式发布，远端结果待回读。[发布记录](verification/2026-10-13-release-148.md)。

2026-10-13最新交付：OPPO已147→148/10348同签名保数据覆盖，删除设置功能审阅。设置Release2项、lint/R8/正式构建通过（lenient）；实际包SHA与冻结一致，安装身份与15条媒体投影保持。[记录](verification/2026-10-13-oppo-release-148-overlay.md)。未自动启动或验证业务；下方旧版/未安装状态为历史。

- 2026-10-13最新源码增量：设置“功能审阅”已删除，项目规则禁止后续添加；Debug编译与设置定向回归通过（lenient）。尚未安装，147冻结包和手机保持此前状态。[记录](verification/2026-10-13-remove-settings-feature-review.md)。

- 当前本地正式包与OPPO均147/10347，Room11；源码WORKTREE未提交、GitHub仍146。原图文故障UNKNOWN，147正常UI验证待做；下列“142/未覆盖”等为此前记录，已由本条安装结果更新。

- 2026-10-13图文：主手机包142（只读核对）；隔离5580既有146正常UI读出指定图集8张并入队，未下载。此次短链连接恢复/受控错误修复59项回归与Debug编译PASS（lenient），原手机异常未复现，修复包设备验收NOT RUN；[证据](verification/2026-10-13-douyin-note-shortlink-recovery.md)。

- 首页系统粘贴整框替换与IME整段链接适配已实现，24项自动回归通过；未覆盖安装，设备效果待验。

- GitHub正式Release：[v1.2.146](https://github.com/nanzhufeng/NanfengDownload-Android/releases/tag/v1.2.146)，APK57,718,955B，远端SHA256与冻结包一致，非Draft/非Pre-release。

- 1.2.146/10346正式同签名包，Room11；源码包含收藏、播放记忆/连续10秒跳转与后台简洁/详细岛，各功能正常UI证据保留其对应版本；146重点为收藏及两条完整HLS，不合称全部由146重新验收。
- 已按用户授权覆盖OPPO142，安装包与正式冻结SHA一致，安装身份及15条媒体元数据保持；未主动启动App进行业务验证。
- 下载采用共享有界Range分块、续传与取消/输出校验；公网效果按各平台独立证据，不声明全平台固定速度。

## In Progress

- 用户142图文读取原始异常仍未闭环：精确142包已在隔离5580成功读取同一8图，主手机原失败证据尚未取得，禁止宣称已修复。新的输入/异步会话修复已覆盖147；正常UI验收仍未执行。

- 首页粘贴替换已完成源码/定向验证，已交付147覆盖，正常UI验收未执行；此次代码、测试与合同文档为LOCAL ONLY。
- 上轮55个合同/文档/Skills/检查器文件已提交并推送main，主交付提交9f19145737b7f03066ab921e30bcf6ae0c381557已远端回读；原146发布不重复。

## Blocked

- 无；测试解释器依赖已定位于当前Android构建的Python依赖目录，已通过Python120项与JS4项，无实际阻塞。

## Tests

| 检查 | 结论 | 时间 / 平台 / 范围 | 证据 |
| --- | --- | --- | --- |
| JVM Debug/Release | PASS | 2026-10-11上轮checkpoint/macOS JBR/146未变源码，各443，0失败/错误/跳过；本轮未重跑 | verification/2026-10-11-checkpoint-experience-increment.md |
| lint/Release/R8 | PASS | 2026-10-11既有146发布，0错误/33警告/3信息；本轮不重建 | verification/2026-10-11-release-146.md |
| Python/JavaScript | PASS | 2026-10-11上轮checkpoint/146未变源码，Python120与JS4；本轮未重跑 | verification/2026-10-11-checkpoint-experience-increment.md |
| 合同/冻结核对 | PASS | 2026-10-12本轮8正文、50审阅来源、30历史分类、4个拒绝探针及479生产源/既有APK | verification/2026-10-12-contract-consistency-audit.md；不替代业务验收 |
| 隔离正常UI | PASS | 146收藏/两条HLS，播放器/岛/取消/字体等沿用各阶段；本轮未操作设备 | 发布及各功能验证记录，不重新标记版本 |
| OPPO正式覆盖 | PASS | 2026-10-11/140→142，同签名/包SHA/媒体元数据与安装身份 | verification/2026-10-11-oppo-release-142-overlay.md |
| OPPO业务/长期后台 | NOT RUN | 不主动操作主设备任务 | 覆盖不替代业务结果 |
| connected/仪器测试 | N/A | 永久禁止 | 项目AGENTS.md |

## Verification Gaps

- 10-13截图手机原始异常仍UNKNOWN；修改包147已正式覆盖，短链恢复与粘贴替换尚未正常UI验收。旧146隔离8图读取不等于手机142故障已消失。

- AVC之外TS与更多长片封装、窄屏多任务空间仍需复核。
- 所有平台每次首次读取≤1秒、所有下载5–10MB/s、OEM长期后台未全面通过，见既有网站/性能验收。
- OPPO私有Room迁移、收藏/播放用户路径未主动验证；真实历史删除仅宿主合成夹具验证。
- 正式APK构建于发布提交之前；479个生产文件已逐一核对与该基线相同，构建时间/签名/版本/包哈希单独记录。

## Sync & Safety

- 2026-10-13增量为DIRTY / LOCAL ONLY：输入源码、对应测试、合同/登记/验证/状态/交接尚未提交；原SDD删除保留。下列推送记录仅属10-12基线。

- Sync checked at: 2026-10-12 12:41 +0800；主交付提交9f19145737b7f03066ab921e30bcf6ae0c381557与远端main一致、ahead/behind=0/0；随后仅状态/交接/审计收尾，不追求文档自身SHA递归相等。
- Code / docs sync: OK（注册/审阅范围）；合同总入口统一领域正文与历史层级，确认冲突记录于10-12审计，目标/实现差距显式保留。生产/应用测试/配置未改，新增维护检查器。
- Git working tree: DIRTY（收尾写入前）；仅原SDD报告删除保持且未纳入交付；本次状态/交接/审计收尾单独提交同步。
- Remote sync: PUSHED（主交付基线）；main已包含先前156f6b2及9f19145737b7f03066ab921e30bcf6ae0c381557，合同/文档/五类Skills/检查器均受控。没有改Release、覆盖设备或访问/修改账单数据。
- Rollback point: UNKNOWN；未把当前底层HEAD默认视为业务稳定回滚点。
- Secrets/config risk: REVIEW NEEDED；本轮文档范围未检出实际凭据；源码签名按字段回退/混配门禁未修复，严格依赖校验路径仍未确认。正式包沿用原身份/字节证据。

## Next

1. 下次构建配置增量补签名整组取值/混配拒绝回归；不重建本轮冻结包。
2. 按新样本验证平台吞吐、更多封装格式及读取时延。
3. 用户明确授权覆盖或业务验收时再核对主设备签名/数据与目标路径。
