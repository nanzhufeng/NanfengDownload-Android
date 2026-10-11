# 146本地检查点、最终回归与正式经验增量

2026-10-11（Asia/Shanghai）。用户要求建立当前代码checkpoint、最终回归、合同/文档固化、增量正式沉淀及核心新文件纳入Git，已完成部分忽略。本轮未改变生产实现、版本、依赖、签名或冻结APK，未操作设备、推送或发布。

## 检查点与范围

- 文档增量前本地标记：`checkpoint/146-before-doc-solidification-20261011`，指向 `110ccb5e2a509f4d002b3ac2ef321d1480d4e8ad`；生产代码仍为 `d0602d13c87411f5225889c04ef9a6f403a2a362`。
- 本轮最终本地标记：`checkpoint/146-final-regression-20261011`。其文档/工具提交覆盖本次合同、经验审计、回归记录、状态/交接、Skill路由和冻结检查器；标签与完整提交号可由Git直接解析，不递归改写状态基线。
- 146原核心源/测试/schema/字体许可已经进入Git；本轮新增工具和两份验证/审计记录显式纳入。浏览器生成物与原本地SDD报告删除保持原样，不恢复、不暂存。
- 共享个人Skill属于仓库外独立资产，更新现有诊断/交付reference、审计模板和路由；改前精确保全，验证与项目Git分别报告。长期记忆未更新。

## 本轮实际运行

| 检查 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| JVM两变体 | 在android目录用JBR运行 `./gradlew -p . testDebugUnitTest testReleaseUnitTest --offline --dependency-verification=off -I ../tools/rerun_unit_tests.gradle`；本轮执行时使用内容完全相同的临时init脚本，现归档为受控工具。仅Test任务强制重跑，不执行assemble/sign | Debug/Release各443，0失败/错误/跳过，BUILD SUCCESSFUL |
| Python | `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=android/app/src/main/python:android/app/build/python/pip/release/common /opt/homebrew/bin/python3.13 -m unittest discover -s android/app/src/test/python` | 120通过，17.118秒；同源固定yt-dlp，合成媒体/本地服务，无公网或付费API |
| JS目标来源 | `node --test android/app/src/test/javascript/douyin-target-video.test.cjs` | 4通过，0失败/跳过 |
| 冻结核对 | `python3 tools/verify_frozen_release.py docs/releases/v1.2.146 android/app/build/outputs/apk/release/南枫下载-Android-v1.2.146.apk --source-ref d0602d13c87411f5225889c04ef9a6f403a2a362` | 479生产文件的受控集合/工作树/提交摘要一致；APK57,718,955B/SHA不变；两个嵌套Python包无宿主缓存文件 |
| 检查器拒绝能力 | 私有临时元数据/自有压缩夹具，不修改生产或正式包 | 错摘要、遗漏生产文件、错APK哈希、嵌套宿主字节码4项均拒绝 |
| 正式沉淀文档 | 通用经验审计校验器，对本次审计及完整开发档案运行 | 结构、链接、占位符与疑似秘密检查通过 |
| Skill结构 | `audit_codex_workflow.py`；项目Skill单独核验frontmatter/路由 | 无硬错误；现有GSAP UI元数据缺失及全局AGENTS软长度警告保留，未扩张修复范围 |
| Skill quick_validate | 默认Python及Python3.13均缺PyYAML | NOT RUN；按Skill维护SOP使用标准库审计兜底，不安装全局依赖 |

回归源片中附加非图像AVC排空标记可能产生本地FFmpeg提示；测试同时核对原源与解码像素，不把此提示当真实丢帧或忽略完整性验证。全部命令退出码与测试统计以本次实际结果为准。

## 已完成部分沿用

146生产代码未变，既有完整lint/Release/R8、正式签名、隔离正常UI两条成品的30/30帧及源像素、安装回拉和GitHub附件/README回读沿用[146发布验收](2026-10-11-release-146.md)。本轮不重建已冻结包、不重新覆盖设备、不重复公网下载；旧证据运行时间不因文档更新刷新。

冻结SHA256仍为 `a9e63e5564518cb5523dfed0e312d9038a7f4e213f0a637d73a203cabd332075`。本轮只证明源码/产物保持与自动回归，不宣称重新验证签名、设备下载或公网性能。

## 文档与合同固化

[共享传输合同](../download-transfer-policy.md)新增提交游标、连续TS排空、Media3压缩样本、精确边界/EOS和完整性定义；[完整开发档案](../南枫下载 Android 完整开发档案.md)修正当前提交与TS封装所有者，旧冲突注明日期；[正式经验审计](2026-10-11-app-experience-increment-audit.md)记录分类、证据矩阵、共享资产和失败门禁。[经验路由](../可迁移开发经验.md)只追加146增量，不重复旧正文。

## 待验证与停止点

固定全平台每次首次≤1秒/持续5–10MB/s、非AVC及更多长片格式、OPPO146业务和OEM后台仍缺相应证据。本轮结束于本地checkpoint；发布与主设备操作只有新授权时再推进，不能把文档/回归当这些业务缺口完成。

既有签名取值按字段回退的配置门禁缺口已在审计及档案明确；本轮只修正文档的过度声明，不改冻结生产源。后续构建配置修复须单独补整组取值与混配拒绝测试。
