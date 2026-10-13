# OPPO 1.2.148 保数据覆盖

2026-10-13，用户明确要求覆盖安装，目标沿用OPPO主手机。

- 本版删除设置功能审阅标题、说明及分隔线；收藏、播放、后台岛与Room11保持。WORKTREE基于819606b，481项生产摘要及正式包信息在docs/releases/v1.2.148/，未提交/推送/发布。
- Release设置定向回归2项通过，0失败/错误/跳过；lint0错误/43警告/3信息，R8与assembleRelease通过。使用lenient依赖校验，严格校验缺口继续保留；147的468项全量结果仅代表其当时源码，本次不重复无关回归。
- 完整用户级App专属Gradle签名来源，非Debug，证书与现场回拉147相同：c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。
- 148/10348正式APK 57,735,339 B，SHA256 f92b31517f73aa33b6d5c37c9a615db9b4e37ead11002c49e24ba39227279905。147已保全，冻结后不重建重签。
- 精确临时APK通过pm install -r --user 0返回Success，147→148；实际安装包回拉SHA等于冻结包。appId/首装时间/用户0 CE与DE inode/dataDir保持，15条MediaStore ID/大小/修改时间投影一致，SHA256 cdb0689b8d1cafa4068d37efbc56d747463934a28e2cb8cb482fc7754808c785。
- 已清理本次临时安装APK。未自动启动App或操作业务、权限、用户数据库；未卸载/清数据/运行仪器或connected测试，未操作其他设备。保留原WIP及SDD删除。
- 未执行148正常UI及原图文故障验证；覆盖成功不代表业务故障闭环，也不代表私有Room全表数据验证。

私有证据：<private-evidence>/nanfeng-overlay148-20261013/，包含构建、签名、前后包管理器/媒体投影、旧新与回拉APK及summary。安装包位于android/app/build/outputs/apk/release/南枫下载-Android-v1.2.148.apk。
