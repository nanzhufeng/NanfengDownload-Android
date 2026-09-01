package com.nanzhufeng.videodownloader.feature.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutResourceTest {
    @Test
    fun compactQueuePanel_usesTheAvailableWorkspaceBetweenHeaderAndFixedAddTaskCard() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val queuePanel = source.substringAfter("private fun QueuePanel(")
            .substringBefore("private fun QueueRow(")

        assertTrue(
            "队列必须占用添加任务之上的完整工作区",
            queuePanel.contains("Column(modifier = Modifier.fillMaxSize())"),
        )
    }

    @Test
    fun queueSelectionAndStartActionStayInOneDirectWorkspace() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val queuePanel = source.substringAfter("private fun QueuePanel(")
            .substringBefore("private fun QueueRow(")

        assertFalse("队列不再进入二次批量编辑模式", queuePanel.contains("batchMode"))
        assertFalse("队列不再显示批量管理入口", queuePanel.contains("批量管理"))
        assertFalse("队列不再需要完成确认", queuePanel.contains("Text(\"完成\")"))
        assertTrue(queuePanel.contains("queue-select-page"))
        assertTrue(queuePanel.contains("queue-start-selected"))
        assertTrue(queuePanel.contains("Text(\"开始下载\""))
        assertFalse(queuePanel.contains("开始下载已选作品"))
        assertTrue(queuePanel.contains("containerColor = MaterialTheme.colorScheme.primary"))
        assertTrue("用户可见名称统一为下载列表", queuePanel.contains("\"下载列表\""))
        assertFalse("不再保留旧的下载队列名称", queuePanel.contains("\"下载队列\""))
        assertTrue("全选应并入队列标题区", queuePanel.contains("queue-selection-inline"))
        assertFalse("全选不再独占一整行", queuePanel.contains("queue-selection-actions"))
    }

    @Test
    fun expandedSidebarOrdersProgressThenQualityThenAddTask() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val expanded = source.substringAfter("internal fun ExpandedHome(")
            .substringBefore("private fun HomeHeader(")

        val progress = expanded.indexOf("TotalProgressCard(")
        val quality = expanded.indexOf("DefaultQualityCard(")
        val addTask = expanded.indexOf("home-side-add-task")
        assertTrue("内屏右栏必须先显示任务总进度", progress >= 0)
        assertTrue("下载质量必须排在任务总进度之后", quality > progress)
        assertTrue("添加任务必须排在右栏最下方", addTask > quality)
    }

    @Test
    fun compactQueuePrioritizesRowsAndExposesAVisibleScrollIndicator() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val queuePanel = source.substringAfter("private fun QueuePanel(")
            .substringBefore("private fun QueueRow(")

        assertFalse("队列状态数量已在运行状态展示，不再重复占用标签行", queuePanel.contains("QueueTab.entries"))
        assertTrue("外屏队列需要占用剩余的完整高度", queuePanel.contains("Modifier.fillMaxWidth().weight(1f)"))
        assertFalse("队列不再被 320dp 上限压缩", queuePanel.contains("heightIn(max = 320.dp)"))
        assertTrue("视频超出可视区时必须显示滚动位置", queuePanel.contains("queue-scroll-indicator"))
        assertTrue("运行任务必须自动滚动到队列可见中心", queuePanel.contains("queueListState.scrollBy"))
    }

    @Test
    fun addTaskSupportCopyLivesInsideTheInputAndSuccessNoticeIsHidden() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val readEntry = source.substringAfter("private fun ReadEntryCard(")
            .substringBefore("private fun Thumbnail(")

        assertTrue(readEntry.contains("placeholder ="))
        assertTrue(readEntry.contains("支持抖音、YouTube、TikTok、哔哩哔哩、小红书链接或分享文本"))
        assertTrue(readEntry.contains("!notice.startsWith(\"已加入\")"))
        assertFalse("字符数不得占用输入框右侧", readEntry.contains("trailingIcon ="))
        assertTrue(readEntry.contains("input-character-count"))
        assertTrue("字符数应在输入框下方独占一行", readEntry.contains("textAlign = TextAlign.End"))
        assertTrue("链接图标必须可复制输入内容", readEntry.contains("copy-input"))
        assertFalse("添加任务标题不再显示加号图标", readEntry.contains("Icons.Outlined.AddCircle"))
        assertTrue("字符数不应挤占链接文字宽度", readEntry.indexOf("input-character-count") > readEntry.indexOf("OutlinedTextField("))
        assertTrue("输入框应使用浅灰底", readEntry.contains("focusedContainerColor = Color(0xFFF1F3F2)"))
        assertTrue("输入框不得保留聚焦黑框", readEntry.contains("focusedBorderColor = Color.Transparent"))
        assertTrue("输入框不得保留常态黑框", readEntry.contains("unfocusedBorderColor = Color.Transparent"))
        assertFalse("清空按钮不得保留描边样式", readEntry.contains("OutlinedButton("))
        assertTrue("清空按钮应使用浅灰底", readEntry.contains("containerColor = Color(0xFFF1F3F2)"))
    }

    @Test
    fun compactAddTaskActionsRemainReachableAboveTheSoftwareKeyboard() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val compactHome = source.substringAfter("internal fun CompactHome(")
            .substringBefore("internal fun ExpandedHome(")
        val readEntry = source.substringAfter("private fun ReadEntryCard(")
            .substringBefore("private fun Thumbnail(")
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue("未打开键盘时必须保留原来的固定首页结构", compactHome.contains("Column("))
        assertFalse("不得为了键盘避让把常态首页改成整页滚动", compactHome.contains("LazyColumn("))
        assertTrue("下载列表必须继续占满原来的剩余工作区", compactHome.contains("Modifier.fillMaxWidth().weight(1f)"))
        assertFalse(
            "adjustResize 已压缩可视窗口，首页不得再叠加整页 IME 留白形成灰色遮挡层",
            compactHome.contains("imePadding()"),
        )
        assertTrue("只允许按系统报告的真实键盘高度计算临时上移", compactHome.contains("WindowInsets.ime.getBottom"))
        assertTrue(compactHome.contains("val keyboardTopPx = view.height.toFloat() - imeBottomPx"))
        assertTrue(compactHome.contains("keyboardLiftPx"))
        assertTrue(compactHome.contains("graphicsLayer"))
        assertTrue("按钮排必须上报真实底边用于精确避让", readEntry.contains("onActionsBottomChanged"))
        assertTrue(readEntry.contains("boundsInRoot().bottom"))
        assertTrue("主 Activity 必须启用 resize 以接收 IME inset", manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    @Test
    fun queueRowsPrioritizeFullVideoInformationAndExpandableTransferDetails() {
        val source = File("src/main/java/com/nanzhufeng/videodownloader/feature/home/FormalHomeSections.kt")
            .readText()
        val queueRow = source.substringAfter("private fun QueueRow(")
            .substringBefore("private fun ResolutionMenu(")

        assertTrue(queueRow.contains("formatBytes(queued.task.totalBytes)"))
        assertTrue("下载详情必须在任务行内展开", queueRow.contains("queue-active-detail"))
        assertTrue("必须显示实时下载速度", queueRow.contains("formatSpeed(queued.task.speedBytesPerSecond)"))
        assertTrue("必须显示已下载和总数据量", queueRow.contains("queued.task.downloadedBytes"))
        assertTrue("必须显示预计剩余时间", queueRow.contains("queued.task.remainingSeconds"))
        assertTrue("卡顿或低速必须使用稳定状态器", queueRow.contains("TransferHealthNoticeController"))
        assertTrue("选择和状态应合并为紧凑前导控件", queueRow.contains("QueueLeadingControl"))
        assertFalse("不再使用抢空间的大号默认 Checkbox", queueRow.contains("Checkbox("))
    }
}
