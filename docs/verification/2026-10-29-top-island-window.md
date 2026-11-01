# 163 下载岛置顶窗口验证

> 合同层级：验证证据。2026-10-29；范围仅南枫下载，不修改南枫转写。

## 原因与实现

162使用TYPE_APPLICATION_OVERLAY，低于系统状态栏。调坐标/elevation无法改变系统窗口层级。截图中的“转写”所属另一个App，不以下载器修改冒充修复它。

163新增最小DownloadIslandTopService，由用户系统页面手动授权。只有实际连接的服务WindowManager/token可创建TYPE_ACCESSIBILITY_OVERLAY；无事件订阅、读屏/手势能力。断开时按owner匹配释放，现有下载队列、通知、停止及输出逻辑不变。普通权限回退到栏下，不能继续承诺状态栏内置顶。置顶窗口限制实际宽度，避免透明边距拦截系统栏手势；息屏主动移除。设置保留原三入口，在既有顶部灵动岛中管理授权，首次开始引导不阻塞传输。

系统无障碍警告由Android显示，用户手动开关；本次隔离设备没有写secure settings代替授权。开启服务后禁止uiautomator，以免测试本身抑制服务；后续仅screencap、只读dumpsys与普通输入取证。早期误点快捷按钮/入口及任务已完成导致浮层消失的调试记录保留，不算产品红灯或置顶证据。

## 代码与构建

- 模式/宿主生命周期回归先红：4项中2项失败；修复后全量release JVM 514项，0失败/错误/跳过。
- testReleaseUnitTest、lintRelease、assembleRelease、R8/Parcelable检查通过；lint 0错误、45警告、3信息。依赖校验lenient，未声称严格依赖验证。
- 精确正式包1.2.163/10363，非Debug；57753271字节，SHA256 `b36d1ac73a0e414d3a97cede912cf43d99b6f2d03887d06e1251217e53249444`，证书SHA256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。
- 493生产源WORKTREE冻结，摘要见[源清单](../releases/v1.2.163/source-manifest.json)与[产物身份](../releases/v1.2.163/artifact.json)。安装到自建隔离模拟器并回拉base.apk，与最终输出/保存包逐字节一致。主手机安装、提交/推送/发布均NOT RUN。
- 两个Chaquopy归档无宿主__pycache__文件；空目录不算宿主字节码。本轮构建前验证七个现存旧APK持久备份，构建后按摘要恢复七个输出，163及生产源ZIP另存非构建目录；原先缺失七版未恢复，见[原事故](2026-10-25-frozen-apk-preservation.md)。

## 正常UI隔离运行

独立AVD `NanfengDownloadIslandTop20261029` / emulator-5580，API35；非主设备，未运行任何connected*AndroidTest或仪器测试。正常输入本地HTTP合成MP4、智能读取、开始下载；未注入任务/Room记录。受控限速用于维持实际活动任务，不能作为公网速度验收。

- 普通权限：APPLICATION_OVERLAY base111000，低于StatusBar151000，frame[84,160][1056,292]，状态栏底144。
- 置顶授权实际绑定：ACCESSIBILITY_OVERLAY base311000，高于StatusBar151000及NotificationShade171000；最终包紧凑frame[266,0][873,137]，宽607px。运行时capabilities=0、eventTypes=[]；无窗口内容/手势能力。
- 在状态栏内部[417,72]点击，真实展开frame[128,0][1012,498]；稳定截图显示标题、进度及查看/停止胶囊。岛外收回；从外侧下拉仍可打开系统通知面板。
- 活动fallback163任务在置顶49%左右，原生设置撤回服务；实时回退APPLICATION_OVERLAY，frame[84,160][1056,292]，之后进度继续。不是仅对已完成任务切换权限。
- 息屏后有异步广播延迟，稳定复核窗口移除；唤醒后恢复。原始即时帧未作为最终通过证据。
- 130%字体仍同一行（截图背景为已打开的通知面板）；2248×2480展开屏和左侧挖孔模拟正常居中、不产生中央虚空。162的中央摄像头几何算法未改，本次没有真实中央摄像头/OEM原生岛。

私有原始日志、WM、截图及联系表位于`.artifacts/island-top-20261029`：最终build163-top-input.log、live163-top/expanded/revoked、verified-screen-off-stable-windows、accessibility-stable、final163-contact.jpg、installed163.apk。较早候选/动画过渡帧与最终包证据分开保存。

## 边界

AOSP已证明新窗口高于系统栏及栏内可点击；ColorOS厂商原生媒体岛实际遮挡、主手机外/内屏及系统授权后的最终效果NOT RUN。主手机本轮不在ADB列表；最后确认现装仍是2026-10-25的162。不得把构建或隔离层级证据写成主手机体验已修复。南枫转写未修改。隔离资源清理见文末收尾记录。

## 收尾

2026-10-29：本次服务原生页面撤权后确认enabled_accessibility_services为空；重置本次AVD分辨率/密度/字体/挖孔并移除本次UI XML，精确关闭并删除NanfengDownloadIslandTop20261029。仅终止已核对完整路径的本地夹具PID49013，56334端口已关闭，未触碰emulator-5596。合同检查8域/80源通过、diff空白检查通过；HEAD不变、index无暂存，原两个删除保留。493源冻结哈希与最终精确APK/持久副本一致。未主设备覆盖或发布。
