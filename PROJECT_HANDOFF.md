# 南枫下载 Android 当前交接

2026-10-11（Asia/Shanghai）。当前项目能力、测试与同步快照看 `docs/PROJECT_STATUS.md`，本文件仅留本次发布接续信息。此前播放器/岛与收藏交接保留在docs/archive对应版本文档，不重写历史证据。

## 本次完成

用户要求“打包上传GitHub”，已上传main并发布正式[v1.2.146](https://github.com/nanzhufeng/NanfengDownload-Android/releases/tag/v1.2.146)。业务代码基线 `d0602d13c87411f5225889c04ef9a6f403a2a362`，发布标签指向 `70cbccaf6fad0abe76eec660cfb93a79be3551c1`（仅追加生产源清单修正文档），当前收尾也只改文档，不改变已验证代码。

复查发现HLS进度回调污染续传检查点，以及连续AVC TS末帧/系统MP4 B帧edit list丢帧。现按已追加片保存检查点、添加TS排空、复用Media3压缩样本封装、明确EOS与样本边界；143/145失败文件/包保留。最终两条146受控成品（含先404后同任务正常UI重试）均30/30帧、131音频帧、全AV解码exit0、逐帧像素与源片一致。

JVM Debug/Release各443、Python120、JS4通过，lint0E/33W/3I，Release/R8通过；详见 `docs/verification/2026-10-11-release-146.md`。README三张146正常模拟器截图已远端核对。本机字节码污染候选143包的问题已精确保全/移出构建，146无宿主字节码文件，不重新构建冻结包。

## 精确产物

源码146/10346，Room11。APK `android/app/build/outputs/apk/release/南枫下载-Android-v1.2.146.apk`，57,718,955B；SHA256 `a9e63e5564518cb5523dfed0e312d9038a7f4e213f0a637d73a203cabd332075`，证书 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。正式/冻结/5580安装回拉及GitHub附件完全一致；附件英文名NanfengDownload-Android-v1.2.146.apk，附SHA256SUMS.txt。生产文件清单和摘要见docs/releases/v1.2.146。

## 保全与设备

原有源码/测试/文档WIP已纳入发布；原浏览器会话、旧私有QA图及本地SDD报告删除仍保留在本地，未上传生成物。文档主机路径/序列号改占位引用，原WIP快照私有保全。上游Noto许可保持原始字节，空白检查对此许可作已记录例外。

仅操作emulator-5580/NanfengWebParityTmp，当前146，原1920×1920/420dpi恢复。61943受控服务器、对应reverse及本次设备临时XML已精确清理，无活跃下载FGS。56条原历史URI保持，旧成品与樱花6e失败缓存保留；旧受控iframe任务在正常UI过程中重新失败，不宣称全部旧字段不变。本次合成成品/失败证据保留，不删除历史制造干净状态。

OPPO仅上轮已按授权覆盖142；本轮未启动/操作/覆盖主设备，其他模拟器未操作。无connected Android/仪器测试、Debug部署、数据库注入、卸载、清数据/缓存。私有原始验证证据未上传。

## 下一安全动作

发布已完成，不重复生成/替换146包；用户要求覆盖时先核对设备、现装签名和指纹，同签名递增保数据，安装不能替代业务验收。

所有平台每次首次≤1秒、持续5–10MB/s、OEM长期后台、AVC之外TS及窄屏多任务空间仍待复核。原A–D、樱花6e/URI120及合格125、YouTube181合格/180偏移失败边界看旧验证记录；不把本轮受控HLS或发布当作全部公网平台完成。
