---
name: nanfeng-download-android-development
description: Implement or refactor a scoped 南枫下载 Android feature across Compose, Room, discovery, transfer, media processing, or MediaStore. Use when changing user-visible behavior, task lifecycle, platform support, or delivery code in this repository.
---

# 南枫下载 Android 开发

## 输入与产物

输入为一个已授权用户目标、当前源码和相应合同。产物为最小可验证增量、必要迁移/测试与明确验证边界；本Skill不授权发布、主设备测试或数据清理。

## 执行

1. 读根AGENTS、PROJECT_STATUS、PROJECT_HANDOFF及[架构所有者](../../../docs/南枫下载 Android 完整开发档案.md)。核对Android/旧桌面范围、版本、依赖、工作树与适用基线，保全其他WIP。
2. 写清触发输入、预期结果、失败/重复/取消与恢复；定位唯一所有者和全部入口，先判断是否已有合同可复用。用户明确只做文档时不修改业务实现。
3. 遵守输入→发现→Room→Worker→来源/传输→处理→发布→历史；UI经Repository，设置/播放展示状态经各自所有者。不要新增跨层旁路。
4. 来源增量同时核对官方域、作品身份、短链/重定向、请求头与最终主机凭据范围；Generic或元数据快路径不能替换已验证的图集/播放器来源。
5. 持久增量统一检查entity、DAO、schema、migration、旧值默认语义、去重/重试/收藏/历史删除，先写最窄的行为红灯，再实施修复。
6. 传输/媒体增量读[传输合同](../../../docs/download-transfer-policy.md)。分别处理直链Kotlin和HLS/DASH Python路径、同目标独占、有序提交、样本/配置/时基、取消及本次输出回滚；无损路径不隐式编码。
7. UI/播放/后台增量按既有组件与合同处理自适应切换、旧回调、权限、可见性和真实队列投影；用户已授权入口直接落实，只有超出范围的新增权限/数据后果才需澄清。
8. 调用$nanfeng-download-android-testing完成改变边界所需的最低充分门禁，再由$nanfeng-download-android-code-review审查，$nanfeng-download-android-doc-sync固化证据。

## 完成与停止

回报改动所有者、用户结果、自动与真实环境证据、保留的WIP和未验收项。访问限制绕过、未知签名迁移、删除用户数据或无授权主设备/远端动作超出本流程；停止相应动作，继续可安全完成部分。

## 合同漂移门禁

先读[合同总入口](../../../docs/contracts/README.md)定位唯一正文；项目根运行 `python3 tools/check_contracts.py`。来源/合同有变化时先语义审阅和对应验证，再更新registry的已审阅摘要，禁止仅重算摘要越过审查。检查通过不替代功能/构建/设备结果；旧方案/交接只作历史参考。
