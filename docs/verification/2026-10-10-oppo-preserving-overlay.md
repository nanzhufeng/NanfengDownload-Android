# OPPO 正式保数据覆盖（2026-10-10 10:38）

用户在五平台审查完成后明确要求“覆盖安装”，本次授权仅用于正式包更新，不扩展为主设备业务测试、下载任务操作、卸载、清数据或远端发布。

- 目标：已连接OPPO PKH120，Android16，物理视口1140×2616，density520/override442，font_scale1.0；仅user0。未操作两个模拟器。
- 包：com.nanzhufeng.videodownloader，1.2.97/10297 → 1.2.100/10300；安装前后均非Debuggable。
- 精确冻结产物：`android/app/build/outputs/apk/release/南枫下载-Android-v1.2.100.apk`，46,236,679B。源APK、设备安装暂存和安装后回拉base SHA-256均为9062c9c7de9eb5b1a8193cbc3fda7af7e807a941be10562da51eb8e8c743eb27。
- 安装前回拉旧base及最终base经apksigner验证；前后证书SHA-256均c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547。
- 执行：明确绑定OPPO序列号，push至本轮专用`/data/local/tmp/`文件，`pm install -r --user 0`返回Success；没有直接adb install、卸载、pm clear、DB写入、Debug/仪器包或connected*AndroidTest。安装后精确删除本轮暂存并确认不存在。
- 包数据目录指纹前后一致：appId10613，CE inode1788131，DE inode1745695，dataDir `/data/user/0/com.nanzhufeng.videodownloader`；首装时间仍2026-08-21 02:04:16。
- MediaStore归属于本包的15条媒体记录，按ID/大小/加入时间/修改时间/相对路径排序后的摘要前后一致：14529dae28f93f628766cd851b816fbaf7de9909c9e9eec99f20edbf0260e609。未输出私人文件名。
- 安装前后pidof均无App进程，没有自动启动/页面或任务操作。私有数据库受Android保护，未尝试提权读取；未核验其任务/历史数量或账号会话内容，目录与媒体元数据指纹不能代替整库逻辑哈希。

私有证据 `<private-evidence>/nanfeng-oppo-overlay-20261010/`（700、文件600）保留旧/新base、冻结target、包元数据及前后指纹；不将原始设备材料/私人媒体记录提交。自动门禁与5580真实样本沿用 `2026-10-10-five-platform-read-download-audit.md`，本次没有重建冻结APK。OPPO读取速度、真实下载/播放和哔站网络问题仍未做主设备业务验收；安装成功不能替代这些结论。没有推送或发布。
