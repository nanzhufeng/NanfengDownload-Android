# Project Status

> 当前源码及适用验证优先；历史阶段见PROJECT_HANDOFF和日期verification。

## Verified

- Last verified: 2026-10-16；branch main，Code baseline: COMMIT `453bcbbb2f15ff38cae9c2920eafbad240dc4aed`；版本1.2.149/10349，Room11。
- Verification scope: 481项生产源与冻结APK逐项一致，真实媒体/隔离验收及主设备覆盖分开核对；源码提交、版本标签、正式Release和附件均已远端回读。
- Status: Active；Verified by: Codex。

## Current

- 修复AAC连续起始零时间封装失败，保留所有编码样本与中途倒退门禁。[故障验证](verification/2026-10-16-aac-clamped-prefix.md)。
- 同作品159段原字节完整HLS经隔离正常UI封装/发布/历史/冷进程重开通过；18555视频样本及29892AAC样本保留，逐帧像素与音频PCM一致。
- 冻结149正式包已按用户授权同签名保数据覆盖OPPO；实际安装包回拉SHA一致，首装时间/数据目录标识及15条媒体元数据投影保持，未自动启动业务。[覆盖记录](verification/2026-10-16-oppo-release-149-overlay.md)。
- 粘贴替换、输入代次隔离、收藏/播放器及删除功能审阅沿用既有实现；本版UI不变，README预览保留注明148的真实页面。
- 149已正式发布，源码/标签与精确APK附件及校验文件回读一致；[发布记录](verification/2026-10-16-release-149.md)。旧冻结146/147/148保留。

## Tests

| 检查 | 结论 | 范围 |
| --- | --- | --- |
| 149定向AAC/ADTS/时基 | PASS | 新回归先红后绿，原大幅倒退拒绝保留 |
| 149 Release JVM | PASS | 469项，0失败/错误/跳过；lenient |
| 149 lint/R8/正式构建 | PASS | 0错误/43警告/3信息；lenient；冻结后未重建 |
| 149隔离真实媒体 | PASS | 同作品HLS前缀复现与完整159段字节回放；全片/逐帧/历史重开 |
| OPPO149正式覆盖 | PASS | 同签名、精确包、安装身份及媒体元数据；非私有Room全表证明 |
| 主设备真实下载/长期后台 | NOT RUN | 不以覆盖替代用户路径 |
| connected/仪器测试 | N/A | 永久禁止 |

## Verification Gaps

- 抖音限时日常7695664553459322978在App可播放，网页来源链路未取得媒体，仍未解决；[现有诊断](verification/2026-10-14-douyin-7695664553459322978.md)。
- 原手机142图文读取故障未复现，根因UNKNOWN；其他环境成功及短链重试不等于原故障闭环。
- 未验证超过250ms起始音频提前、未知编码/容器、跨系统突然死亡及极大历史/目录；不宣称所有平台秒读或固定吞吐。
- 严格依赖校验缺口与签名字段回退未拒绝混配仍保留；本次执行使用完整受控签名来源。
- 主设备全表迁移、真实业务、OEM长期后台未由覆盖安装证明。

## Sync & Safety

- Git working tree: 源码/合同/验证/Release资料已提交，当前仅本次交付状态收尾；原SDD删除排除且保留。
- Remote sync: main生产提交、标签及正式Release已回读一致，APK/校验附件名称、大小、digest匹配；仅收尾文档随后同步。只用已覆盖OPPO的冻结包，不重复签名或操作设备。
- 不操作账单数据、凭据、私有媒体或数据库；Rollback point: UNKNOWN，旧Release不等于允许降级用户数据。

## Next

1. 149正式交付已完成；后续源码变更另行递增版本，冻结包不重建。
2. 真实主手机任务由用户验收，不自动重试下载。
3. 按具体红灯推进限时日常、其他媒体及既有验证缺口。
