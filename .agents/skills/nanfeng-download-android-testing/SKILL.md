---
name: nanfeng-download-android-testing
description: Select, run, and report safe evidence for 南枫下载 Android changes. Use for unit tests, lint, release builds, isolated emulator acceptance, OPPO formal overwrite checks, or claims about real media downloads.
---

# 南枫下载 Android 测试与验收

## 选择证据

先写所需声明，再选证明层：源码/自动行为、lint构建、隔离正常UI、实际媒体、正式覆盖、主设备用户路径、公网性能分别独立。无生产变化可复核适用旧证据，旧运行时间不刷新。

## 执行

1. 核对AGENTS、当前版本/依赖/工作树、测试源码及[验证入口](../../../docs/南枫下载 Android 完整开发档案.md)。先运行改变边界的JVM/Python/JS用例，再按变化范围扩展，不为凑数量运行无关测试。
2. Android工程在android目录。使用匹配JDK；macOS现有环境用Android Studio JBR。未冻结新增量按需执行testDebugUnitTest、testReleaseUnitTest、lintRelease及assembleRelease；发布需相关完整门禁。不要执行connected*AndroidTest或instrumentation部署。
3. 如果需要确定单测实际重跑，用 `./gradlew testDebugUnitTest testReleaseUnitTest -I ../tools/rerun_unit_tests.gradle`。完整记录offline/依赖校验选项；UP-TO-DATE、构建成功和测试文件数不能当实际执行用例数，汇总XML失败/错误/跳过。
4. 宿主Python导入同源固定依赖：项目根运行 `PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=android/app/src/main/python:android/app/build/python/pip/release/common python3.13 -m unittest discover -s android/app/src/test/python`。release依赖目录缺失时先定位已有匹配环境，不为测试重建冻结包；标明依赖/FFmpeg不足造成的未运行或跳过。
5. JS项目根运行 `node --test android/app/src/test/javascript/douyin-target-video.test.cjs`。来源目标、推荐排除、官方响应与不可信拒绝属于解析契约，不能直接算实际下载。
6. 迁移检查entity/DAO/导出schema/全部受影响迁移；公开行为夹具保旧字段、收藏和来源版本。内存SQLite/JVM不代替真实主设备Room升级。
7. 隔离验收仅使用本任务明确设备和正常输入/读取/选择/开始/暂停/停止/重试/历史路径；不以手工Intent、Activity参数、数据库或插入媒体造结果。记录测试前后目标身份与不变范围，恢复本次显示/代理/reverse/自建服务，精确清理自有临时资源。
8. 成品从实际MediaStore/SAF回读：URI数量、格式/大小、实际音视频全片解码；无损封装还核对样本、时间轴和首尾/源像素。历史播放与冷进程重开、设备重启是不同验证，不互相冒充。
9. 性能分别记真实网络字节、成品字节、各阶段/总耗时、平均/峰值、连接、重试与回退。缓存命中、本地服务、元数据、合并耗时不能代替公网持续吞吐。
10. 冻结包只用tools/verify_frozen_release.py核对既有APK/清单/指定生产提交，不重建重签；签名/安装/业务另验。主设备覆盖须明确授权、递增同签名正式非Debug包、前后数据指纹与安装回拉校验，不自动启动业务。

## 回报

按PASS/FAIL/PARTIAL/NOT RUN/N/A分层列命令、版本、时间、环境、样本与边界；注明现存lint/依赖校验缺口，交给doc-sync。永久禁止项写N/A，不尝试绕过补齐。

## 合同漂移门禁

先读[合同总入口](../../../docs/contracts/README.md)定位唯一正文；项目根运行 `python3 tools/check_contracts.py`。来源/合同有变化时先语义审阅和对应验证，再更新registry的已审阅摘要，禁止仅重算摘要越过审查。检查通过不替代功能/构建/设备结果；旧方案/交接只作历史参考。
