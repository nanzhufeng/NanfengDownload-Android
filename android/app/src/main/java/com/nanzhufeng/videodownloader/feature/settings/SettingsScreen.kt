package com.nanzhufeng.videodownloader.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.BuildConfig
import com.nanzhufeng.videodownloader.core.ui.AppCardTone
import com.nanzhufeng.videodownloader.core.ui.PlatformIcon
import com.nanzhufeng.videodownloader.core.ui.WorkbenchPageHeader
import com.nanzhufeng.videodownloader.core.ui.ControlSurface
import com.nanzhufeng.videodownloader.core.ui.WorkbenchCard
import com.nanzhufeng.videodownloader.core.ui.ForestGreen
import com.nanzhufeng.videodownloader.core.ui.WarmOrange
import com.nanzhufeng.videodownloader.data.settings.AppSettings
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import com.nanzhufeng.videodownloader.domain.session.SiteSessionState

@Composable
fun SettingsScreen(
    settings: AppSettings,
    sessions: List<SiteSessionState>,
    onResolutionSelected: (ResolutionPreset) -> Unit,
    onAutoResumeChanged: (Boolean) -> Unit,
    onOpenLogin: (SessionSite) -> Unit,
    onImportYoutubeCookies: (String) -> Unit,
    onClearSession: (SessionSite) -> Unit,
    onMaxConcurrentDownloadsSelected: (Int) -> Unit = {},
    onFileNameRuleSelected: (FileNameRule) -> Unit = {},
    onCustomTreeSelected: (String, String) -> Unit = { _, _ -> },
    onUseSystemStorage: () -> Unit = {},
    onExportCookies: (SessionSite, String) -> Unit = { _, _ -> },
    expanded: Boolean,
) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    val overviewScroll = rememberLazyListState()
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var networkDialogVisible by remember { mutableStateOf(false) }
    BackHandler(enabled = section != null) { section = null }
    var backgroundDialogVisible by remember { mutableStateOf(false) }
    var concurrencyDialogVisible by remember { mutableStateOf(false) }
    var namingDialogVisible by remember { mutableStateOf(false) }
    var storageDialogVisible by remember { mutableStateOf(false) }
    var exportConfirmationSite by remember { mutableStateOf<SessionSite?>(null) }
    var pendingExportSite by remember { mutableStateOf<SessionSite?>(null) }
    val cookiePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.toString()?.let(onImportYoutubeCookies)
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        onCustomTreeSelected(uri.toString(), context.displayName(uri.toString()))
    }
    val cookieExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val site = pendingExportSite
        pendingExportSite = null
        if (site != null) uri?.toString()?.let { onExportCookies(site, it) }
    }

    if (qualityDialogVisible) {
        ChoiceDialog(
            title = "下载质量",
            options = ResolutionPreset.entries.map { it to it.label() },
            selected = settings.defaultResolution,
            onSelected = { onResolutionSelected(it); qualityDialogVisible = false },
            onDismiss = { qualityDialogVisible = false },
        )
    }
    if (networkDialogVisible) {
        AlertDialog(
            containerColor = Color.White, tonalElevation = 0.dp,
            onDismissRequest = { networkDialogVisible = false },
            title = { Text("恢复网络后自动继续") },
            text = {
                SettingSwitchRow(
                    title = "自动继续",
                    summary = "仅恢复因网络中断而等待的任务，用户暂停的任务不会自动继续。",
                    checked = settings.autoResumeNetwork,
                    onCheckedChange = onAutoResumeChanged,
                )
            },
            confirmButton = { TextButton(onClick = { networkDialogVisible = false }) { Text("关闭") } },
        )
    }

    if (backgroundDialogVisible) DownloadBackgroundSettingsDialog { backgroundDialogVisible = false }

    if (concurrencyDialogVisible) {
        ChoiceDialog(
            title = "同时下载",
            options = (1..3).map { count -> count to "$count 个任务" },
            selected = settings.maxConcurrentDownloads,
            onSelected = {
                onMaxConcurrentDownloadsSelected(it)
                concurrencyDialogVisible = false
            },
            onDismiss = { concurrencyDialogVisible = false },
        )
    }
    if (namingDialogVisible) {
        ChoiceDialog(
            title = "文件命名",
            options = FileNameRule.entries.map { it to it.label() },
            selected = settings.fileNameRule,
            onSelected = {
                onFileNameRuleSelected(it)
                namingDialogVisible = false
            },
            onDismiss = { namingDialogVisible = false },
        )
    }
    if (storageDialogVisible) {
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { storageDialogVisible = false },
            title = { Text("下载路径") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageChoice(
                        title = "系统媒体库",
                        summary = "视频保存到 Movies，MP3 保存到 Music",
                        selected = settings.customTreeUri == null,
                        onClick = {
                            onUseSystemStorage()
                            storageDialogVisible = false
                        },
                    )
                    StorageChoice(
                        title = "选择其他文件夹",
                        summary = settings.customTreeName ?: "由系统文件选择器授权",
                        selected = settings.customTreeUri != null,
                        onClick = {
                            storageDialogVisible = false
                            folderPicker.launch(null)
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { storageDialogVisible = false }) { Text("关闭") }
            },
        )
    }
    exportConfirmationSite?.let { site ->
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { exportConfirmationSite = null },
            title = { Text("导出 ${site.label} 登录会话？") },
            text = {
                Text(
                    "cookies.txt 可用于登录 ${site.label} 账号。任何拿到该文件的人都可能使用该会话；" +
                        "请仅保存到自己可信的本地位置，不要上传、转发或存入共享网盘。",
                )
            },
            dismissButton = { TextButton(onClick = { exportConfirmationSite = null }) { Text("取消") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportConfirmationSite = null
                        pendingExportSite = site
                        cookieExporter.launch("${site.name.lowercase()}-cookies.txt")
                    },
                ) { Text("继续选择位置") }
            },
        )
    }
    val stateFor: (SessionSite) -> SiteSessionState = { site ->
        sessions.firstOrNull { it.site == site }
            ?: SiteSessionState(site, false, "未保存登录会话")
    }
    val youtube = stateFor(SessionSite.YOUTUBE)
    val bilibili = stateFor(SessionSite.BILIBILI)
    val douyin = stateFor(SessionSite.DOUYIN)
    val tiktok = stateFor(SessionSite.TIKTOK)
    val xiaohongshu = stateFor(SessionSite.XIAOHONGSHU)
    if (section != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("settings-details"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    WorkbenchPageHeader(
                        when (section) {
                            "accounts" -> "账号与权限"
                            "export" -> "导出登录会话"
                            else -> "关于"
                        },
                        expanded,
                    ) {
                        IconButton(onClick = { section = null }, modifier = Modifier.testTag("settings-back")) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "返回设置")
                        }
                    }
                }
                if (section == "export") {
                    lazyColumnItems(SessionSite.entries, key = { it.name }) { site ->
                        ExportSessionRow(
                            state = stateFor(site),
                            onExport = { exportConfirmationSite = site },
                        )
                    }
                } else {
                item {
                    if (section == "accounts") {
                        SettingsCard("平台登录", compact = !expanded,
                            modifier = Modifier.testTag("settings-platform-sessions")) {
                    Text(
                        "单个公开视频默认无需登录；批量主页、播放列表或受限内容再按提示登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("长视频平台", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        SessionRow(
                            state = youtube,
                            modifier = Modifier.testTag("settings-youtube"),
                            onOpen = { cookiePicker.launch(arrayOf("text/plain", "application/octet-stream")) },
                            onClear = { onClearSession(SessionSite.YOUTUBE) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        SessionRow(
                            state = bilibili,
                            modifier = Modifier.testTag("settings-bilibili"),
                            onOpen = { onOpenLogin(SessionSite.BILIBILI) },
                            onClear = { onClearSession(SessionSite.BILIBILI) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    Column(
                        modifier = Modifier.testTag("settings-short-video-platforms"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("短视频平台", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        SessionRow(
                            state = douyin,
                            modifier = Modifier.testTag("settings-douyin"),
                            onOpen = { onOpenLogin(SessionSite.DOUYIN) },
                            onClear = { onClearSession(SessionSite.DOUYIN) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        SessionRow(
                            state = tiktok,
                            modifier = Modifier.testTag("settings-tiktok"),
                            onOpen = { onOpenLogin(SessionSite.TIKTOK) },
                            onClear = { onClearSession(SessionSite.TIKTOK) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        SessionRow(
                            state = xiaohongshu,
                            modifier = Modifier.testTag("settings-xiaohongshu"),
                            onOpen = { onOpenLogin(SessionSite.XIAOHONGSHU) },
                            onClear = { onClearSession(SessionSite.XIAOHONGSHU) },
                        )
                    }
                        }
                    } else {
                        SettingsCard("应用信息", compact = !expanded,
                            modifier = Modifier.testTag("settings-about-details")) { AboutSettingsContent() }
                    }
                }
                }
            }
        }
        return
    }

    val settingsContent = listOf(
        SettingsContent(key = "account") {
            SettingsCard("账号与会话", compact = !expanded,
                modifier = Modifier.testTag("settings-account-card")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionSetting(
                        title = "账号与权限", icon = Icons.Outlined.ManageAccounts,
                        onClick = { section = "accounts" }, modifier = Modifier.testTag("settings-accounts-entry"),
                    )
                    ActionSetting(
                        title = "导出登录会话", icon = Icons.Outlined.FileUpload, value = "cookies.txt",
                        summary = "文件含登录凭据，请仅保存到可信位置",
                        onClick = { section = "export" }, modifier = Modifier.testTag("settings-export-cookies"),
                    )
                }
            }
        },
        SettingsContent(key = "download") {
            SettingsCard("下载与保存", compact = !expanded,
                modifier = Modifier.testTag("settings-download-card")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionSetting(
                        title = "下载质量", icon = Icons.Outlined.HighQuality, value = settings.defaultResolution.label(),
                        onClick = { qualityDialogVisible = true }, modifier = Modifier.testTag("settings-quality-entry"),
                    )
                    ActionSetting(
                        title = "后台下载与灵动岛", icon = Icons.Outlined.NotificationsActive, value = "权限与提醒",
                        onClick = { backgroundDialogVisible = true }, modifier = Modifier.testTag("settings-background-island"),
                    )
                    ActionSetting(
                        title = "恢复网络后自动继续", icon = Icons.Outlined.Wifi, value = if (settings.autoResumeNetwork) "开启" else "关闭",
                        onClick = { networkDialogVisible = true }, modifier = Modifier.testTag("settings-network-entry"),
                    )
                    ActionSetting(
                        title = "同时下载", icon = Icons.Outlined.PlaylistPlay, value = "${settings.maxConcurrentDownloads} 个任务",
                        onClick = { concurrencyDialogVisible = true }, modifier = Modifier.testTag("settings-concurrency"),
                    )
                    ActionSetting(
                        title = "文件命名", icon = Icons.Outlined.DriveFileRenameOutline, value = settings.fileNameRule.label(),
                        onClick = { namingDialogVisible = true }, modifier = Modifier.testTag("settings-file-name"),
                    )
                    ActionSetting(
                        title = "下载路径", icon = Icons.Outlined.FolderOpen, value = settings.customTreeName ?: "系统媒体库",
                        onClick = { storageDialogVisible = true }, modifier = Modifier.testTag("settings-storage-location"),
                    )
                }
            }
        },
        SettingsContent(key = "about") {
            SettingsCard("关于", compact = !expanded,
                modifier = Modifier.testTag("settings-about-card")) {
                ActionSetting(
                    title = "关于", icon = Icons.Outlined.Info, value = "${BuildConfig.VERSION_NAME}",
                    onClick = { section = "about" }, modifier = Modifier.testTag("settings-about-entry"),
                )
            }
        },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = overviewScroll,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()
                .testTag(if (expanded) "settings-expanded-menu" else "settings-screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SettingsHeader(expanded) }
            lazyColumnItems(settingsContent, key = { it.key }) { entry -> entry.content() }
        }
    }
}

@Composable
private fun AboutSettingsContent() {
    val repositoryLine = remember {
        buildAnnotatedString {
            append("源码与更新： ")
            withLink(
                LinkAnnotation.Url(
                    url = RepositoryUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = WarmOrange,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(RepositoryLabel)
            }
        }
    }

    ProvideTextStyle(MaterialTheme.typography.bodySmall) {
    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("南枫下载", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "本机媒体下载与历史管理工作台。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("版本信息", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("Android 版 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
            Text("构建时间 ${BuildConfig.BUILD_TIMESTAMP}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("开发者信息", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("开发者：席瑞")
            Text("联系邮箱：nanzhufeng.studio@gmail.com")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val textMeasurer = rememberTextMeasurer()
                val baseStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                val measuredWidth = textMeasurer.measure(repositoryLine, style = baseStyle, softWrap = false).size.width
                val fit = ((constraints.maxWidth - 1).coerceAtLeast(1).toFloat() / measuredWidth.coerceAtLeast(1))
                    .coerceAtMost(1f)
                Text(
                    text = repositoryLine,
                    modifier = Modifier.testTag("settings-about-github-link"),
                    style = baseStyle.copy(fontSize = baseStyle.fontSize * fit, lineHeight = baseStyle.lineHeight * fit),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text("版权所有 © 2026 席瑞")
        }
    }
    }
}

@Composable
private fun SettingsHeader(expanded: Boolean) {
    WorkbenchPageHeader("设置", expanded)
}

private data class SettingsContent(
    val key: String,
    val content: @Composable () -> Unit,
)

@Composable
private fun SettingsCard(
    title: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    tone: AppCardTone = AppCardTone.NEUTRAL,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkbenchCard(
            tone = tone,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(if (compact) 12.dp else 14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ExportSessionRow(
    state: SiteSessionState,
    onExport: () -> Unit,
) {
    Surface(
        onClick = onExport,
        enabled = state.hasSavedSession,
        modifier = Modifier.fillMaxWidth().testTag("settings-export-${state.site.name.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlatformIcon(
                platform = state.site.platform(),
                contentDescription = "${state.site.label} 图标",
                modifier = Modifier.size(30.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.site.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (state.hasSavedSession) "可导出" else "暂无会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.hasSavedSession) ForestGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.let {
                    if (state.hasSavedSession) it else it.copy(alpha = 0.38f)
                },
            )
        }
    }
}

@Composable
private fun SessionRow(
    state: SiteSessionState,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformIcon(
            platform = state.site.platform(),
            contentDescription = "${state.site.label} 图标",
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.site.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                state.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (state.hasSavedSession) {
            Button(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.width(58.dp).height(34.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF1F3F2),
                    contentColor = ForestGreen,
                ),
            ) { Text("清除", style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.width(8.dp))
        }
        Button(
            onClick = onOpen,
            enabled = state.site != SessionSite.XIAOHONGSHU,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.width(78.dp).height(34.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ControlSurface,
                contentColor = ForestGreen,
                disabledContainerColor = ControlSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            ),
        ) {
            Text(
                when (state.site) {
                    SessionSite.YOUTUBE -> if (state.hasSavedSession) "重新导入" else "导入"
                    SessionSite.XIAOHONGSHU -> "无需登录"
                    else -> when {
                        !state.hasSavedSession -> "登录"
                        state.isAuthenticated && state.site == SessionSite.DOUYIN -> "查看账号"
                        state.isAuthenticated -> "重新登录"
                        else -> "继续登录"
                    }
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun SessionSite.platform(): DownloadPlatform = when (this) {
    SessionSite.YOUTUBE -> DownloadPlatform.YOUTUBE
    SessionSite.DOUYIN -> DownloadPlatform.DOUYIN
    SessionSite.TIKTOK -> DownloadPlatform.TIKTOK
    SessionSite.BILIBILI -> DownloadPlatform.BILIBILI
    SessionSite.XIAOHONGSHU -> DownloadPlatform.XIAOHONGSHU
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ControlSurface)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionSetting(
    title: String,
    icon: ImageVector,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(if (summary == null) CircleShape else RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = SettingsEntryIconColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        value?.takeIf(String::isNotBlank)?.let { currentValue ->
            Spacer(Modifier.width(12.dp))
            Text(
                currentValue,
                modifier = Modifier.widthIn(max = 220.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "打开${title}设置",
            tint = SettingsEntryIconColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ReadOnlySetting(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            value,
            modifier = Modifier.widthIn(max = 220.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (option, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelected(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelected(option) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StorageChoice(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun android.content.Context.displayName(uri: String): String = runCatching {
    contentResolver.query(
        android.net.Uri.parse(uri),
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull().orEmpty().ifBlank { "自定义文件夹" }

private fun ResolutionPreset.label(): String = when (this) {
    ResolutionPreset.BEST -> "最佳画质"
    ResolutionPreset.UP_TO_1080P -> "1080p 及以下"
    ResolutionPreset.UP_TO_720P -> "720p 及以下"
    ResolutionPreset.UP_TO_360P -> "360p 及以下"
    ResolutionPreset.AUDIO_MP3 -> "仅音频 MP3"
}

private fun FileNameRule.label(): String = when (this) {
    FileNameRule.DATE_AND_TITLE -> "发布日期 + 视频标题"
    FileNameRule.TITLE_ONLY -> "视频标题"
    FileNameRule.CREATOR_AND_TITLE -> "作者 + 视频标题"
}

private const val RepositoryUrl = "https://github.com/nanzhufeng/NanfengDownload-Android"
private const val RepositoryLabel = "GitHub · nanzhufeng/NanfengDownload-Android"

private val SettingsEntryIconColor = Color(0xFF666666)
