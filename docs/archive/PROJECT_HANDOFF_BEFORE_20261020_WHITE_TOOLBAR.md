> 合同层级：历史交接。

# 南枫下载 Android 当前交接

## 当前结果

2026-10-20：用户确认157可清除但很慢；158通过每次清理共享SAF父目录检查提速，同规模隔离实测3589→1112ms（减少约69%）。[性能证据](../verification/2026-10-20-history-cleanup-performance.md)。主手机当前未连接，本轮覆盖NOT RUN；最后已核对的覆盖为157，不能称158已装手机。

## 精确基线与验证

- main HEAD843b53506add27b29ce6343063640907807f03e6；已发布生产仍c8008e4/152，Room11。当前WORKTREE158/10358、488生产源冻结；[身份](../releases/v1.2.158/artifact.json)、[清单](../releases/v1.2.158/source-manifest.json)。包57735339字节，SHA256为78d4b9197f95e22870f1bd8933485ceb1b43ed964918b63a380a1681a9f6f832，同原正式证书、非Debug，冻结后不重建/重签。
- 同次清理按授权树/父目录共享卷、持久读权限及相关子ID结果，已证明缺失的后代不重复失败打开/叶子/路径回退；提交前fresh卷/权限及负面目录重查，重现/未知不提交，取消传播。卡片/详情不复用清理快照；多输出与Room身份/收藏快照守卫保持，媒体不删除。
- Release502项、lint0错误/44警告/3信息、R8/assemble通过，依赖校验lenient；模型77条缺失+1有效目录调用154→3（不等于手机实测）。最终精确包正常UI同4文件下载/保留根删除子目录/一次清理，二次0删除、冷进程与有效62911字节/限定媒体元数据保持；AVD安装包回拉一致。
- 后台/账号/关于及157原清理修复保持。[157回归](../verification/2026-10-19-saf-subdirectory-cleanup-and-background-dialog.md)、[157覆盖](../verification/2026-10-19-oppo-release-157-overlay.md)。158主手机真实耗时及77条清理NOT RUN；无connected或仪器测试。

## 保全与接续

- 149–157旧APK原字节保持，157/158生产源有本机私有完整快照；153起的源码/合同WIP与原SDD删除保持，未暂存/提交/推送/发布。私有日志/XML/媒体不入Git。
- 主手机恢复连接后，仅可沿现有覆盖授权完成同证书递增正式158保数据更新：先核现装身份/首装/dataDir/ce与de inode及限定媒体投影，唯一临时APK→pm install -r --user 0→回拉验签和字节→清临时APK。不自动启动或操作清理/下载；不得卸载或清数据。发布/提交依后续明确授权。
- 专用AVD资源与保存路径收尾以本轮报告为准；不要操作其他模拟器。原157交接完整见[本轮前快照](../archive/PROJECT_HANDOFF_BEFORE_20261020_CLEANUP_PERFORMANCE.md)；后续缺口见[状态](../PROJECT_STATUS.md)、长期规则从[合同入口](../contracts/README.md)路由。
