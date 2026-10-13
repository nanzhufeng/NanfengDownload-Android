# 2026-10-13 抖音图文读取与短链网络恢复

## 已确认事实与边界

用户分享McY3WMbS3Tk图文，截图为“读取作品失败，平台未返回可识别的原因”。用户要求自行核对版本后，仅只读主手机包元数据，确认1.2.142/10342；未启动/操作主手机业务或数据。

- 宿主公开短链请求返回302，官方目标为/share/note/7692742276019207507/。跟踪参数和原始响应只留私有临时证据。
- 宿主同源DouyinShortLinkResolver实际网络解析成功，得到https://www.douyin.com/note/7692742276019207507；临时联网测试验证后移除，不将不稳定公网测试纳入常规回归。
- 原NanfengWebParityTmp指向的临时目录已不存在，未清理/覆盖它。在本任务新建NanfengDownloadGallery20261013并使用明确隔离端口5580；其他模拟器未操作。
- 新隔离环境安装既有冻结正式146包，正常启动、输入短链、点击智能读取。日志显示target=true、route=douyin/note，structured=true、expected=8、candidates=8；UI显示“已读取抖音图文并加入1个图集任务”。该证据是既有146的目标读取/入队，不是此次修复包、下载完成或主手机142通过。
- 未点击开始下载，因此无新成品/解码/历史播放声明。主手机截图的原始异常未取得，同症状未在隔离环境复现，根因仍UNKNOWN，不能声称此次实际手机失败已闭环。

## 确认缺口与修复

DouyinShortLinkResolver原先单次网络IOException直接结束，没有短链级恢复；DiscoveryFailurePresenter对未知英文网络错误可能输出通用“平台未返回原因”。这是源码及注入网络故障可复现的稳定性/诊断缺口，不等于已经证明该用户故障的具体异常。

- 整条短链跳转链只允许一次网络补试；成功路径不增加请求，不增加等待。
- 持续失败使用DouyinShortLinkReadException区分DNS、超时与连接中断。DiscoveryFailurePresenter输出该受控阶段消息，不拼接内部主机/原始异常详情给用户。
- 线程取消、TLS异常、HTTP访问拒绝、非官方域/凭据/非标准端口拒绝不重试；原跳转次数和同作品身份门禁保持。
- 保留此前链接框粘贴替换WIP，图集提取/队列/下载/数据库不改。

## 红绿验证

新增瞬时连接错误→下一次官方note跳转、持续DNS错误两项测试，在原代码上失败；9项运行中2失败。修复后及补充边界最终59项通过：短链11、发现引擎24、输入编辑9、首页15，0失败/错误/跳过，Debug生产及测试Kotlin编译通过。附加HTTP403不重试、跨跳共享一次预算、TLS/取消不重试、受控错误不泄漏底层细节。

命令：Android Studio JBR，`android/gradlew -p android :app:testDebugUnitTest --tests '*DouyinShortLinkResolverTest' --tests '*PlatformSourceDiscoveryEngineTest' --tests '*LinkInputEditingTest' --tests '*HomeViewModelTest' --dependency-verification lenient`。使用lenient，既有严格依赖校验缺项没有被修复或隐去。

合同检查、定向diff/空白检查收尾执行。未重建冻结146、未正式打包/安装此次改动、未提交/推送。原SDD报告删除保留，不操作账单数据。隔离实例在证据采集后关闭，数据目录保留；只删除本次自建UI dump文件。

## 后续验收缺口

修复包需正常UI回归及获授权正式覆盖后在用户原网络重试；当前不能由59项测试和旧146读取成功替代142手机故障消失。输入法粘贴设备验收沿用上份记录的待验状态。
