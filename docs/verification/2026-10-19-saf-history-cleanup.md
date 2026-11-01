# 删除自定义保存目录后，失效历史清理回归

2026-10-19，修复工作树1.2.153/10353，Room11。底层HEAD843b53506add27b29ce6343063640907807f03e6；保留既有合同整理WIP及原SDD删除，未提交、推送、发布或覆盖主手机。

## 已确认故障与原因

专用Android35 AVD通过正常首页读取并下载自制2秒视频到系统选择的Documents目录，形成真实SAF历史与持久读写授权；确认目录内只有本次夹具后，精确删除本次目录。旧152重新进入历史显示“视频已不存在”，点击清理确认后同一记录仍在。普通MediaStore夹具删除后的152清理通过，故本轮不把整个清理算法归咎为失效。

真实本地ExternalStorageProvider在授权目录已删除时，后代URI的open/query/findDocumentPath会先因树归属检查失败抛IllegalArgumentException；此时主卷仍mounted、原树持久读授权仍存在。树本身查询返回null，独立findDocumentPath则明确抛FileNotFoundException。原实现只在打开抛FileNotFoundException时复核，且要求可读树根；因此把已删除目录留在UNKNOWN。卡片另将无法读取统称不存在，造成“界面红字、清理却保留”的不一致。

初次153尝试补叶子路径查询仍未通过原路径；随后用仅本次诊断包定位上述首个异常分叉，未把构建通过作为修复。最终包已移除全部临时日志，不根据异常文案推断缺失。

## 修复边界

- AndroidLocalMediaInspector打开失败后继续受保护的缺失确证；本地SAF必须卷已挂载、同一树持久读授权有效。叶子检查不明确时独立核实授权树自身；明确空cursor或typed FileNotFoundException才视为缺失，null/普通参数异常/不支持/临时失败仍UNKNOWN。
- 非本地或非树提供方、权限不足及未挂载不扩张删除；未解析错误文字或绕过SAF读原始路径。
- 卡片/详情/外部播放区分检查中、可读、确认缺失和暂时无法读取；旧fileExists=false不阻止重读。清理结束触发卡片重新检查，数据库未变化也不会继续显示旧读取结果。
- 全库扫描、任一成品可读则保留、快照事务核对与仅删历史不删媒体的原合同保持；无数据库迁移或新常驻入口。

## 最终验证

| 检查 | 结论与范围 |
| --- | --- |
| 正常UI红绿 | 152同一SAF残留记录→最终153保数据覆盖→正常历史清理，一次从2条变1条，仅失效记录消失 |
| 重复清理/冷进程 | 再次确认检查全1条，仍保留有效记录；冷进程重开未恢复已删除记录 |
| 有效成品保全 | 62911字节，清理前后逐字节与SHA256相同；MediaStore ID、名称、路径、大小投影完全一致；预览、2秒时长正常 |
| 定向Debug | 初期18项通过；后续最终根因补充由Release回归覆盖 |
| 最终Release JVM | 493项，0失败/错误/跳过；包含后代异常、null cursor、typed缺失、权限/离线、未知异常、取消与展示状态 |
| lint/R8/assemble | PASS，0错误/43警告/3信息；依赖校验lenient，严格校验既有缺口仍在 |
| 正式候选身份 | 同原正式证书，153/10353，非debug；专用AVD实际安装包回拉SHA与候选一致 |
| 源/产物保全 | 487项生产源冻结；149–152旧APK按原字节恢复；嵌套Python包无宿主字节码文件 |

最终精确包57735339字节，SHA256 `86eaa0e21dd93ed40d3b35c050c960ecbf0a9269eb7beb600cf81fc390163cb9`，证书SHA256 `c4fb47e276b5a9381e5362e8d176ccb9e171a034f513c9d811d47a53640f4547`。[冻结清单](../releases/v1.2.153/source-manifest.json)、[产物身份](../releases/v1.2.153/artifact.json)。冻结后不再重建/重签；工作树尚无生产提交，旧冻结提交核对工具不能冒充新版本提交证明。

合同/路由门禁PASS：8合同、71来源、128文档分类、236本地链接；维护工具11项临时树回归通过。此结果只证明维护路由，不替代上面的业务验收。

## 验证限制与证据

仅本任务独立AVD正常UI，无connected或仪器测试、无数据库/媒体库注入；实际历史由下载生成，私有Room仅只读快照。专用AVD的shell删除仅作用于自制夹具目录。主手机仍152，仅只读确认版本与限定媒体元数据；未取得截图原条目实际URI，不能称其已在主手机修复或验收。撤销权限、可移除卷拔插、云提供方和其他OEM未新增真实设备验收，保护分支仅代码/JVM覆盖。

私有原始证据保留在本机临时证据目录，包括旧152清理后XML、系统授权/卷/提供方日志、最终安装包回读、两次清理与冷重开XML、有效文件前后字节；不纳入Git或Release。临时服务器和本次专用模拟器在验收后关闭。

系统API依据：[DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract)，[AOSP DocumentsProvider](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/DocumentsProvider.java)。官方Android15源码与本次实际Provider结果共同核对，未把API声明当设备验收。
