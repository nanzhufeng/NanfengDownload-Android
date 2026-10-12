---
name: nanfeng-download-android-debugging
description: Diagnose 南枫下载 Android link discovery, session, transfer, media validation, MediaStore, history, player, or foldable-state failures. Use when a real download, external platform, task state, output file, or playback path is incorrect or unreliable.
---

# 南枫下载 Android 排错

## 输入与输出

输入是具体失败用户路径、任务/来源身份及允许环境；输出是保全证据、首次可证伪分叉、根因/推测区分、最小修复及同信号回归。仅截图文案不能证明根因。

## 执行

1. 先读AGENTS、当前状态/交接，核对版本/源码与失败记录。分开发现、会话、队列、传输、处理、发布、历史和播放器；记录哪个事实首次不成立。
2. 修改或重试前保全最小原始证据：私有任务/报告、失败缓存与清单、原成品、文件字节/摘要、错误阶段。私有原始证据不进Git；不清缓存或直接整片重下毁掉红灯。
3. 定位[所有者](../../../docs/南枫下载 Android 完整开发档案.md)：discovery/probe来源、session凭据、repository持久状态、download执行、OutputStore发布、feature/navigation展示。先检查前一层输入是否已经错误。
4. 平台/网络排错核对规范来源/作品、官方重定向、目标请求头作用域、响应码/Content-Range/长度/有效字节；Cookie存在、blob播放器、HTTP200不能单独证明可下载。吞吐拆成准备/首字节/网络/处理/发布，不用连接数或峰值当结果。
5. 并行续传排错对照请求完成、有序组装和实际持久提交游标；强制乱序/中断/旧尝试阻塞验证漏片、重片、同目标重入。保有效片，不能为了策略变更删旧布局。
6. 封装/播放排错保源编码流，分别查轨道配置、ADTS/多帧样本、PTS/DTS/时基/结束时间、包数、实际解码帧与首尾内容。已知无损夹具比较源像素；exit0或文件长度不是完整性证明。
7. 先在JVM/MockWebServer/Python自有夹具形成确定红灯；只有所需层级不足时才走明确隔离模拟器正常UI。播放器/后台还核对会话代次、Activity可见、权限与系统/OEM状态。
8. 在首个分叉修复并重跑同一公开接缝；已通过且未受影响路径不反复重下。交由testing选择层级、doc-sync记录失败→修复→新结果与外部限制。

## 边界

不运行connected*AndroidTest；不操作主设备任务，不注入数据库，不卸载/清数据，不伪造来源、成品或成功状态。若用户只要求复盘，记录确认问题与待验证假设，不应用业务修复。
