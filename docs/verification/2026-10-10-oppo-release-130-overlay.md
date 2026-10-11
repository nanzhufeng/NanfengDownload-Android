# OPPO 正式版 1.2.130 保数据覆盖

2026-10-10（Asia/Shanghai）。用户再次要求“覆盖安装”，目标存在此前隔离限制，询问后用户明确选择“OPPO 主手机”。此次授权只用于正式包更新，未扩展到主设备自动下载或真机功能测试。

## 已通过

- 安装前现有包为`com.nanzhufeng.videodownloader`，1.2.100/10300，非debuggable；安装前未发现本App下载前台服务。原包已只读回拉保存。
- 1.2.130/10330正式包57,653,479字节，SHA-256 `a12a37198d79917b7ad67c2eaff187429d74d160aec160b35dfc9c27dc3a5848`，与上一增量冻结包一致；未重新构建或签名。现场apksigner验证原包与新包证书SHA-256均为`c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。新包非debuggable，递增版本；旧版与当前均Room10。
- 精确推送本次临时APK，`pm install -r --user 0`返回`Success`；回拉实际base.apk与冻结及项目正式包完整SHA-256相等。回读版本1.2.130/10330，非debuggable。
- 安装前后包UID、dataDir、首次安装时间及用户0的ceDataInode/deDataInode完全相等。本App拥有的15条MediaStore记录按ID排序，投影ID/大小/修改时间，完整指纹`cdb0689b8d1cafa4068d37efbc56d747463934a28e2cb8cb482fc7754808c785`前后相等。
- 安装后未启动App，未操作下载任务；未发现下载前台服务。本次远端临时APK已精确删除。本次证据目录700/文件600，包含原APK、冻结APK、回拉APK和前后包/媒体指纹。

## 未验证与取证限制

此次只证明正式包覆盖和上述保留指纹。主设备私有数据目录stat受权限限制；使用PackageManager提供的CE/DE目录inode作身份对比，没有root、run-as、数据库注入或权限绕过。首次stat命令的格式参数被ADB shell拆分，修正引用后确认真实限制为Permission denied；首次媒体查询引号同样修正，正式前后指纹均使用正确引用的查询。失败取证没有改变设备数据。

私有Room任务/历史内容未读取，也未打开App触发启动行为；不能把目录身份不变声称为主设备业务全表相等。15条媒体元数据不变不等于全片解码。OPPO后台下载、灵动岛简洁/详细切换、OEM长期稳定性未做新验收；此前隔离模拟器后台结果见`2026-10-10-background-download-island.md`。

无connected Android或仪器测试、Debug部署、卸载、清数据、清缓存、自动启动、任务操作、其他设备变更或推送/发布。本轮未改业务源码或APK，不重跑已通过的自动门禁；其416/416和lint/Release证据沿用对应后台增量记录。

私有证据：`<private-evidence>/nanfeng-oppo-overlay-130-20261010/`；执行结果`summary.json`、`install-result.txt`，原始包信息和媒体投影仅保存在私有目录。
