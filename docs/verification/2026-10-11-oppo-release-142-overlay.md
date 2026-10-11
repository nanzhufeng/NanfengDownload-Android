# OPPO 1.2.142 同签名保数据覆盖

2026-10-11（Asia/Shanghai）。用户要求“覆盖安装”，延续此前明确指定的OPPO主手机，只安装南枫下载已冻结正式142包。

- 现场只读确认现装1.2.140/10340、非Debug、无本App下载前台服务；appId、dataDir、firstInstallTime、用户0 CE/DE inode与上轮140安装后证据一致。旧base.apk回拉SHA为上轮已验140正式SHA，旧/新包apksigner均通过且证书相同。
- 精确142包57,718,955 B，SHA256 `6ec877c3a9f119d3603c4e23aa58f0b2f12aef8d4f91c2111f6cba5ac42b2a71`，与开发阶段冻结142相同；包名com.nanzhufeng.videodownloader、1.2.142/10342、非Debug。证书SHA256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`；没有重建或重签。
- 安装前再次核对数据目录身份、版本、MediaStore投影及下载服务。推送至本次唯一临时APK路径，执行 `pm install -r --user 0` 返回Success，无失败或自动重试。
- 安装后实际版本142/10342，回拉base.apk完整SHA与冻结/正式包一致，安装身份及首次安装时间不变。本App15条媒体ID/大小/修改时间投影完全相等，指纹SHA `cdb0689b8d1cafa4068d37efbc56d747463934a28e2cb8cb482fc7754808c785`。
- 临时APK精确清理并确认不存在；没有启动App、操作任务/权限/设置、卸载、清数据/缓存、Debug部署、仪器/connected Android测试、推送或发布。其他设备仅ADB库存列示，没有设备操作。

门禁及隔离正常UI证据见 `2026-10-11-history-favorites.md`：Debug/Release各439通过、Room10→11旧列保全、收藏/取消/冷重开/宽窄切换与字体130%通过；源码和冻结包未变，没有重复构建或测试。

主手机私有Room未读取，安装并不证明其迁移、收藏用户路径、下载性能或长期后台已实际通过；仅核验数据目录身份与媒体元数据保留。用户首次打开后的真实业务仍待使用验收，不自动启动主设备进行补验。

私有证据 `<private-evidence>/nanfeng-oppo-overlay-142-20261011/`，目录700、包/指纹证据600，含preflight、旧/新/实际回拉包、包元数据、签名、媒体投影、安装清理结果及summary。全部已有未提交改动保留。
