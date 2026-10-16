# OPPO 1.2.149 保数据覆盖

2026-10-16，用户明确“覆盖安装”，沿用此前指定OPPO主手机。使用已完成隔离验收的冻结149包，没有重建或重签。

- 现装148/10348与149/10349包名均为com.nanzhufeng.videodownloader；证书SHA256同为c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。149为正式非Debug，481生产文件与冻结WORKTREE清单一致。
- APK57735343字节，SHA256 b82e8ceeb4fd244d3bfeaa9ef0c673b3f584c576517346782d50580362730bf1；推送设备临时文件后pm install -r --user 0返回Success。
- 回拉实际安装base.apk与精确冻结包SHA一致；安装版本149/10349。首次安装时间、ceDataInode/deDataInode和dataDir均保持；本应用15条MediaStore记录的ID、文件名、字节数、修改时间投影逐字节一致。不是私有Room全表或所有媒体内容逐字节审计。
- 已清理此次设备临时APK，保全原148安装包和只读前后证据于私有目录。未自动启动业务，未重试手机任务、清缓存、卸载、清数据、仪器测试或操作另一模拟器；未提交、推送或发布。
- AAC故障的先红后绿、469项Release回归、lint/R8及完整媒体/历史验证见2026-10-16-aac-clamped-prefix.md，均保原隔离范围。此次安装不替代主手机业务验收。
