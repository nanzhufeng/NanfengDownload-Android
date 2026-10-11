# OPPO 1.2.140 同签名保数据覆盖

2026-10-10（Asia/Shanghai）。用户要求“覆盖安装”，延续其此前明确指定的OPPO主手机目标，只安装下载器；用户取消的转写器设置改动没有实施。

- 安装前现场确认1.2.132/10332、非Debug、无本App下载前台服务；appId、dataDir、首次安装时间及用户0 CE/DE inode与上次132覆盖记录相同。只读回拉旧base.apk验签。
- 精确140正式包57,686,187 B，SHA-256 `dbde17429dd3b460b730e75e1c736b8c6b1975e1983ed77da7c2d38a9b973c90`；与开发阶段冻结包一致，未重建/重签。旧包与新包apksigner均通过，证书SHA-256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547` 相同；新包1.2.140/10340、非Debug、递增版本。
- 用本次独立临时路径推送，安装前再次确认无活跃下载服务，`pm install -r --user 0` 返回Success；安装后版本/签名边界及实际回拉base.apk完整SHA与正式包相同。没有失败或安装重试。
- 安装前后appId/dataDir/firstInstallTime/CE与DE inode保持；本App15条MediaStore记录的ID/大小/修改时间投影完全相等，指纹SHA-256 `cdb0689b8d1cafa4068d37efbc56d747463934a28e2cb8cb482fc7754808c785`。私有Room未读；数据目录身份和媒体元数据保留不能代替业务全表检查或全片解码。
- 未启动App、未操作下载队列/设置/权限，安装后无下载前台服务；远端临时APK精确删除并确认不存在。没有卸载/清数据/缓存清理/仪器测试/connected Android测试/Debug部署/推送/发布。

自动门禁与隔离正常UI验证沿用 `2026-10-10-video-playback-memory-and-seek.md`，没有重复无变化的435项测试。OPPO新的播放器/灵动岛实际用户路径、真实下载吞吐及OEM长期后台仍需实际使用验收；覆盖成功不代表这些业务目标已真机通过。

私有证据 `<private-evidence>/nanfeng-oppo-overlay-140-20261010/`，包含旧包/冻结包/实际回拉包、包信息/媒体投影、preflight、安装/清理结果与summary；目录700/文件600。全部已有未提交改动保留。
