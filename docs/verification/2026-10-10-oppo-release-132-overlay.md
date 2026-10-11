# OPPO 正式版 1.2.132 保数据覆盖

2026-10-10（Asia/Shanghai）。用户在132修复完成后要求“覆盖安装”，按此前明确指定的OPPO主手机延续同签名保数据更新授权。只更新正式包，不启动软件、不操作下载任务、不扩张为性能或后台真机验收。

- 现场读取旧包1.2.130/10330、非debuggable，与前次覆盖的appId、dataDir、首次安装时间及用户0 CE/DE目录inode相同；原base.apk只读回拉。安装前未发现本App下载前台服务。
- 新包1.2.132/10332，57,669,863 B，正式/冻结/安装后回拉SHA-256均为`a12e08caa223ce676e4ff2560a9e5c26355f672224a27a797ab23a7e66d9b739`。原包与新包现场apksigner验证均通过，证书SHA-256均为`c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`，递增版本、非debuggable。未重建或重签。
- 将冻结包推到本次独立临时路径，`pm install -r --user 0`返回Success；回读1.2.132/10332并回拉实际base.apk验证完整SHA相等。
- 安装前后appId、dataDir、首次安装时间、用户0 CE/DE inode全部相等；本App拥有的15条MediaStore记录ID/大小/修改时间按ID排序指纹相等，SHA-256 `cdb0689b8d1cafa4068d37efbc56d747463934a28e2cb8cb482fc7754808c785`。私有Room未读取，目录身份保留不能称业务全表逐列验证；媒体元数据保留不能称全片解码。
- 未启动App或操作任务；安装后未发现本App下载前台服务。本次远端临时APK已删除并确认不存在，私有证据目录700/文件600。没有任何其他设备操作、connected Android/仪器测试、Debug部署、卸载、清数据、缓存清理、暂存/提交/推送/发布。

自动门禁及隔离正常UI真实下载、全片解码、历史冷播放证据沿用`2026-10-10-shared-slow-download-recovery-and-island-permission.md`，不重跑无变化测试。OPPO真实下载速度、灵动岛授权/模式、OEM长期后台仍待用户实际使用验证，不把覆盖成功称为这些问题已经真机通过。

私有证据`<private-evidence>/nanfeng-oppo-overlay-132-20261010/`，包含原包/冻结包/实际回拉包、安装前后包信息与媒体投影、`preflight.json`、`install-result.txt`、`cleanup-result.txt`、`summary.json`。此次没有失败或重试安装。
