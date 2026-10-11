# 2026-10-11 历史视频收藏验收

## 范围与实现

用户要求历史视频更多操作增加收藏、所有操作配统一图标、全部时间右侧进入全部收藏视频。收藏经Repository保存到Room历史行；10→11显式默认0迁移，归档事务保留既有收藏；取消收藏不改原文件、URI和时间。音频/图集不进入视频收藏，失效标记保留收藏；收藏视图隐藏全局失效清理。稳定合同见 `../history-favorites.md`，设置功能审阅已同步。

## 已通过

- Debug/Release JVM各439项，失败/错误/跳过均0。新增3项收藏筛选行为测试及1项真实宿主SQLite迁移测试；旧schema创建→迁移→逐列保全→收藏持久重开→取消收藏→删除记录关联消失全部通过。
- 正式命令：JBR下 `android/gradlew -p android testDebugUnitTest testReleaseUnitTest lintRelease assembleRelease --offline --dependency-verification=off`。lint0错误/33警告/3信息，Release/R8/Parcelable通过。Python/JS业务未改，不复用旧测试数作新结果。
- 仅emulator-5580/NanfengWebParityTmp以正式同签名递增包140→141→142 `pm install -r --user 0`；安装身份不变，正式/冻结/安装回拉SHA一致。仅正常首页→历史UI，不注入数据库、媒体、Activity extras或绕过入口。
- 两条已有受控成品通过菜单收藏。搜索已有无匹配关键词后进入收藏，自动清空旧限制并显示全部两条。所有六项操作使用20dp图标，收藏卡片小星标、入口选中反馈正常。
- 最终142宽屏→窄屏、字体130%切换后收藏筛选仍选中，恰好两条卡片；无普通历史混入，无全局失效清理。联系表检查字体/图标/菜单，无遮挡。
- force-stop后正常重开再进入收藏，仍是两条；逐一取消后列表2→1→0，空状态正确。取消收藏后退出筛选，两条仍在普通历史；收藏卡片正常进入内置视频播放界面，再正常返回。
- 140升级前59任务/56历史/56媒体，142保存两项及最后取消后，全部旧列逐列相等，原成品URI保持；仅新isFavorite列按正常UI改变。最后测试收藏恢复0，没有新增下载、删历史或改媒体。
- 原1920×1920/420dpi、font_scale1.0已恢复；本次临时设备XML精确清理，无活跃下载FGS。旧缓存、失败证据、媒体及既有未提交修改保留。

## 已修复的失败

- 红灯：收藏过滤尚未实现时3项测试中2项失败；迁移未实现时真实SQLite报缺少isFavorite列。补齐实际过滤与ALTER后转绿。
- 141正常UI切换宽窄布局会重建HistoryScreen并丢失筛选开关，显示普通历史；收藏行数据未丢。142将开关提升到自适应分支外共享可保存状态，严格检查入口checked=true及卡片数，重验通过。141失败截图/日志保留。
- 首次样式编译使用了现有Material3不支持的containerColor参数，已改为兼容表面背景，最终完整编译通过。
- 验证脚本曾用错播放器关闭控件名称，使后续断言失败；没有作为通过结果。采用正常返回关闭播放器后，重新完成1→0、普通历史与最终数据校验。

## 产物与证据

1.2.142/10342，Room11，HEAD d9a66a1，混合未提交工作区。正式APK `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.142.apk`，57,718,955 B；SHA256 `6ec877c3a9f119d3603c4e23aa58f0b2f12aef8d4f91c2111f6cba5ac42b2a71`；证书SHA256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。142冻结后未重建替换。

私有证据 `<private-evidence>/nanfeng-favorites-141-20261011/`：红/绿及完整构建日志、schema迁移前后只读快照、`data-final-proof.json`、`artifact-142.json`、`tests-142.json`、正常UI XML、`qa-142-contact.jpg`、冻结140/141/142包。仓库不存用户媒体、数据库或会话。

## 未验证与边界

开发验证阶段未操作OPPO；随后用户要求“覆盖安装”，已140→142同签名保数据安装，详见 `2026-10-11-oppo-release-142-overlay.md`。未启动或操作主设备App，不能将覆盖结果或隔离UI当成OPPO业务/OEM结果。永久未运行connected Android/仪器测试，无Debug部署、卸载、清数据/缓存、推送、发布。宿主迁移测试使用合成夹具，设备快照只读；真实历史删除只按宿主夹具验证，没有删除用户现有记录。

本次没有新下载或平台服务调用，不替代旧读取/下载速度、A–D、樱花、公网五平台、长期后台验收。旧网络交接见 `../archive/PROJECT_HANDOFF_BEFORE_20261010_VIDEO_PLAYBACK_MEMORY.md`；本轮前交接完整保留于 `../archive/PROJECT_HANDOFF_BEFORE_20261011_HISTORY_FAVORITES.md`。
