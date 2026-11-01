# 南枫下载交接

> 合同层级：当前交接。规范见[合同入口](docs/contracts/README.md)，现状见[项目状态](docs/PROJECT_STATUS.md)。

2026-11-01：163已正式发布GitHub，[交付证据](docs/verification/2026-11-01-release-163.md)。生产提交`0bd6a6de7dbea91d81c659b448878a864f4ecf37`，注释标签v1.2.163固定至该提交；main随后仅补文档。仅一个APK附件，公开回拉/摘要/逐字节及README四图回读通过，无额外校验附件。

- 冻结163/10363，493源，57753271字节，SHA b36d1ac73a0e414d3a97cede912cf43d99b6f2d03887d06e1251217e53249444，原正式证书/非Debug，[身份](docs/releases/v1.2.163/artifact.json)。精确包与源ZIP存.artifacts/frozen-apks，冻结后不重建/重签。
- 本轮Debug/Release各514项及lint无错误通过（45警告3信息，lenient）；R8/assemble与AOSP置顶/栏内展开/撤权回退/息屏沿用10月29日同包证据。当前四图明确模拟器与采集日期，不代替ColorOS验收。
- 主手机最后已确认163于2026-10-29 12:03保数据覆盖，[覆盖证据](docs/verification/2026-10-29-oppo-release-163-overlay.md)。本轮发布未操作主设备；用户需手动授权“南枫下载灵动岛置顶”并验收原生岛实际效果。南枫转写未修改。
- 失效清理/性能、设置菜单及既有修复已随163发布；抖音限时日常、原手机图文读取根因、OEM长期后台/真实业务等未验证缺口保留。旧七版APK缺失仍见[原事故](docs/verification/2026-10-25-frozen-apk-preservation.md)，不得重建冒充。
- 原无关SDD删除未提交；本次自建AVD和夹具已精确清理，不影响其他设备。下一步按新反馈处理，后续覆盖须用户明确授权及主User0保数据门禁；禁止主设备uiautomator/无障碍接管及自动业务测试。
