> 合同层级：历史交接。

# 南枫下载交接

> 合同层级：当前交接。唯一领域规范见[合同入口](../contracts/README.md)。

2026-10-29：仅修南枫下载。163用活无障碍服务token创建置顶窗口，普通悬浮回退到栏下；用户须系统手动授权，服务不订阅事件、不读屏/手势。实际窗口宽度限于岛，息屏移除，队列/通知/停止不变。[本轮证据](../verification/2026-10-29-top-island-window.md)。

- main HEAD843b53506add27b29ce6343063640907807f03e6未变，已发布生产c8008e4/152，Room11。WORKTREE163/10363，493源冻结；[身份](../releases/v1.2.163/artifact.json)、[清单](../releases/v1.2.163/source-manifest.json)。57753271字节，SHA b36d1ac73a0e414d3a97cede912cf43d99b6f2d03887d06e1251217e53249444，原正式证书/非Debug，冻结后不重建/重签。
- 514项JVM/lint/R8/正式包通过，lint45警告3信息，依赖校验lenient。隔离API35证实ACCESSIBILITY_OVERLAY311000高于系统栏151000，栏内展开、外侧系统手势、息屏隐藏、活动下载撤权回退通过，精确安装包回拉一致。原始证据在.artifacts/island-top-20261029。ColorOS原生岛/主手机体验NOT RUN，不等同于已修复主手机截图；南枫转写未修改。
- 主手机已于2026-10-29 12:03由162保数据覆盖至163；同签名递增、精确包回拉、主User0四项身份指纹及15条限定媒体投影通过，[覆盖记录](../verification/2026-10-29-oppo-release-163-overlay.md)。未启动/操作业务或更改授权；置顶需用户在系统无障碍设置手动开启“南枫下载灵动岛置顶”，ColorOS实际效果仍NOT RUN。
- 本轮七个现存旧APK先验证持久备份后构建，输出已逐摘要恢复；163+生产源ZIP也存.artifacts/frozen-apks。原事故缺150/151/153/154/155/156/158七版仍未恢复，不重建冒充原件；[事故记录](../verification/2026-10-25-frozen-apk-preservation.md)。
- 原WIP/SDD及测试删除保留，无暂存/提交/推送/发布。[修改前交接](../archive/PROJECT_HANDOFF_BEFORE_20261029_TOP_ISLAND.md)、[状态](../PROJECT_STATUS.md)。本轮覆盖已完成，下一步由用户手动授权及验收实际效果；主设备禁止uiautomator/无障碍接管或自动业务测试。隔离资源收尾记录见本轮证据末尾。
