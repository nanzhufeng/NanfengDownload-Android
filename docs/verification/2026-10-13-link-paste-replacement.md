# 2026-10-13 链接输入粘贴替换

## 范围与依据

用户要求Android首页无需清空即可粘贴新链接替换旧内容，参考GitHub电脑版最新效果。开始HEAD为819606b76de4a9c163147fe52599a806e1587616，main；本报告是WORKTREE验证，未提交/推送/发布/安装。

实际读取GitHub电脑版main解析出的698f13fe661b9ab9c9f07b4900982ddf29144c0d及该SHA的app/torrent_input.py：DownloadInput.keyPressEvent在Paste快捷键中select(Document)后insertText，光标在末尾。Android原ReadEntryCard直接将系统编辑字符串传给onInputChange，因此粘贴会追加/仅替换选区。

## 实现与入口

- FormalHomeSections.ReadEntryCard接入ReplaceOnPasteInput，紧凑/展开布局共用，既有外观/复制按钮/清空/字符计数保持。
- 系统长按菜单与Ctrl+V/Shift+Insert明确粘贴动作读取剪贴板全文并替换；空内容不擦除。
- LinkInputEditing从原选区与新值识别IME一次插入的HTTP(S)链接文案，保全文案/多链接；不后台读剪贴板。普通编辑、中文组合输入、光标选区变化保留。
- onInputChange仍是唯一草稿出口，只在文本改变时调用；已有ViewModel/Settings同步路径未改。系统分享/恢复草稿、队列/下载/历史/数据库均未改。

## 验证

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| JVM输入编辑 | PASS | LinkInputEditingTest 9项，0失败/错误/跳过；首尾/中间/选区替换、同链接重贴、空剪贴板、整段多链接/长文本、普通修改/删除/中文组合态 |
| 首页状态回归 | PASS | HomeViewModelTest 15项，0失败/错误/跳过 |
| Debug生产及测试Kotlin编译 | PASS | 随上述任务编译，使用dependency-verification lenient；不是正式包构建 |
| 默认严格依赖检查 | FAIL | 既有依赖校验元数据缺项；未修改配置或自动接受新校验记录 |
| offline首次尝试 | FAIL | emoji2:1.3.0缓存缺失，尚未执行测试；之后联网解析 |
| 隔离正常UI/OEM输入法 | NOT RUN | 不把JVM编辑值测试当系统菜单或具体输入法验收 |
| 正式构建/覆盖安装 | NOT RUN | 冻结146不重建，未操作设备或用户数据 |

实际成功命令：android目录，Android Studio JBR，`./gradlew :app:testDebugUnitTest --tests '*LinkInputEditingTest' --tests '*HomeViewModelTest' --dependency-verification lenient`。XML来自android/app/build/test-results/testDebugUnitTest；编译/测试成功不消除严格依赖门禁失败。

## 审阅与保全

仅改变输入适配与对应测试/文档；未改来源解析、下载、成品、Room、签名、版本、依赖配置。原.superpowers/sdd/fidelity-settings-report.md删除保留。未读取/修改账单数据。合同登记与git diff --check收尾核对。

待验：设备正常长按粘贴、输入法剪贴板建议、外接键盘及横竖屏；OEM把粘贴拆成多个组合提交时可能保持普通插入，不能宣称全输入法验收。当前保持既有清空按钮作为显式动作。
