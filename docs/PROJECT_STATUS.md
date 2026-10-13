# Project Status

> 当前源码、适用验证与远端回读优先；历史阶段详见日期verification及PROJECT_HANDOFF。

## Verified

- Last verified: 2026-10-13；branch main，生产提交 `65467beee743b04f1e3d2d5d8fb895b7fd126d02`，版本148/10348、Room11。
- Verification scope: COMMIT；481项生产文件与冻结清单、精确APK一致，verify_frozen_release.py通过；含新增生产文件，不以版本号替代源码核对。
- Status: Active；Verified by: Codex。

## Current

- 已实现新链接粘贴替换、输入切换取消旧读取及分页清理、捕获请求归属与目标匹配；删除设置功能审阅，根AGENTS禁止再次添加。
- 正式148包已保数据覆盖OPPO：实际安装包回拉SHA一致，安装身份及15条媒体元数据保持；未自动启动业务。
- 本次隔离5580正常UI验证148设置/关于及历史空状态，README预览为本次真实页面；恢复显示并停止自有模拟器，保留数据。
- [148发布记录](verification/2026-10-13-release-148.md)；GitHub源码/标签和正式Release已发布，APK及校验附件大小/远端SHA256均回读一致；非Draft、非Pre-release。

## Tests

| 检查 | 结论 | 范围/证据 |
| --- | --- | --- |
| 147 Release JVM | PASS | 468项；本次148仅删除设置文字并递增版本，保留原运行范围 |
| 148设置Debug/Release | PASS | 各2项；依赖校验lenient |
| 148lint/R8/正式构建 | PASS | 0错误/43警告/3信息；lenient；冻结后未重建 |
| 合同/源码/包 | PASS | 8合同/57来源；481生产源、提交与冻结包只读核对 |
| 148隔离正常UI | PASS | 设置/关于、历史空状态及README截图；不是全部功能验收 |
| OPPO148正式覆盖 | PASS | 同签名、包SHA、安装身份及媒体元数据；不读取私有Room |
| 主设备业务/长期后台 | NOT RUN | 覆盖安装不代替业务结果 |
| connected/仪器测试 | N/A | 永久禁止 |

此前146 Python120/JS4及媒体/播放/岛验收保留原版本和样本，不标为148重跑。具体证据见[148覆盖](verification/2026-10-13-oppo-release-148-overlay.md)、[147覆盖](verification/2026-10-13-oppo-release-147-overlay.md)及[架构审查](verification/2026-10-13-input-discovery-architecture-audit.md)。

## Verification Gaps

- 原手机142图文读取失败未复现，原始异常与根因UNKNOWN；旧142/146隔离8图读取、短链重试/文案与此次发布均不等于故障闭环。
- 148真实粘贴所有IME入口、捕获Activity全部生命周期、真实读取/下载及OEM长期后台未全面验收。
- AVC以外TS、更多长片封装及元数据、跨系统突然死亡恢复、极大历史/目录需独立验证。
- 不宣称所有平台首次≤1秒、持续5–10MB/s；平台/CDN/网络边界与既有性能样本保持。
- 严格依赖校验存在缺口，当前构建用lenient；源码签名字段回退未自动拒绝混配，但本次执行核验完整单一签名来源。
- 私有Room全表/迁移/收藏与播放真实用户路径未由覆盖安装证明。

## Sync & Safety

- 用户已授权本次提交main并正式发布148；生产提交65467be与远端main、标签v1.2.148已回读一致，正式Release附件已回读校验完成。
- 当前仅收尾文档增量及原无关SDD报告删除；SDD删除不纳入提交。冻结146/147/148不重建或重签；不操作账单数据。
- Rollback point: UNKNOWN；旧发布存在不等于允许覆盖降级或回滚用户数据。

## Next

1. 发布回读已完成；后续变更另行递增版本，不重建148。
2. 对用户实际失败保留原异常，按真实链路收敛根因，不以诊断文案替代修复。
3. 按具体红灯处理严格依赖、签名整组门禁及余下媒体/性能验收。
