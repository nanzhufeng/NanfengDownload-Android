package com.nanzhufeng.videodownloader.feature.settings

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.ui.AppCardTone
import com.nanzhufeng.videodownloader.core.ui.PlatformIcon
import com.nanzhufeng.videodownloader.core.ui.WorkbenchCard
import com.nanzhufeng.videodownloader.core.ui.ForestGreen
import com.nanzhufeng.videodownloader.core.ui.QualityPurple
import com.nanzhufeng.videodownloader.core.ui.SecondaryText
import com.nanzhufeng.videodownloader.core.ui.StorageOchre
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
    var concurrencyDialogVisible by remember { mutableStateOf(false) }
    var namingDialogVisible by remember { mutableStateOf(false) }
    var storageDialogVisible by remember { mutableStateOf(false) }
    var exportSitePickerVisible by remember { mutableStateOf(false) }
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
    if (exportSitePickerVisible) {
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { exportSitePickerVisible = false },
            title = { Text("选择要导出的平台") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SessionSite.entries.forEach { site ->
                        val state = sessions.firstOrNull { it.site == site }
                        TextButton(
                            onClick = {
                                exportSitePickerVisible = false
                                exportConfirmationSite = site
                            },
                            enabled = state?.hasSavedSession == true,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${site.label}${if (state?.hasSavedSession == true) "" else "（暂无会话）"}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { exportSitePickerVisible = false }) { Text("取消") } },
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
    val settingsContent = listOf(
        SettingsContent(key = "account") {
            SettingsCard(
                title = "账号与权限",
                icon = Icons.Outlined.Key,
                accent = ForestGreen,
                compact = !expanded,
                modifier = Modifier.testTag("settings-account-card"),
            ) {
                Text(
                    "单个公开视频默认无需登录；批量主页、播放列表或受限内容再按提示登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("长视频平台", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    SessionRow(
                        state = youtube,
                        modifier = Modifier.testTag("settings-youtube"),
                        onOpen = { cookiePicker.launch(arrayOf("text/plain", "application/octet-stream")) },
                        onClear = { onClearSession(SessionSite.YOUTUBE) },
                    )
                    HorizontalDivider()
                    SessionRow(
                        state = bilibili,
                        modifier = Modifier.testTag("settings-bilibili"),
                        onOpen = { onOpenLogin(SessionSite.BILIBILI) },
                        onClear = { onClearSession(SessionSite.BILIBILI) },
                    )
                }
                HorizontalDivider()
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
                    HorizontalDivider()
                    SessionRow(
                        state = tiktok,
                        modifier = Modifier.testTag("settings-tiktok"),
                        onOpen = { onOpenLogin(SessionSite.TIKTOK) },
                        onClear = { onClearSession(SessionSite.TIKTOK) },
                    )
                    HorizontalDivider()
                    SessionRow(
                        state = xiaohongshu,
                        modifier = Modifier.testTag("settings-xiaohongshu"),
                        onOpen = { onOpenLogin(SessionSite.XIAOHONGSHU) },
                        onClear = { onClearSession(SessionSite.XIAOHONGSHU) },
                    )
                }
            }
        },
        SettingsContent(key = "quality") {
            SettingsCard(
                title = "下载质量",
                icon = Icons.Outlined.Tune,
                accent = QualityPurple,
                tone = AppCardTone.PURPLE,
                compact = !expanded,
                modifier = Modifier.testTag("settings-quality-card"),
            ) {
                ResolutionPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (settings.defaultResolution == preset) QualityPurple.copy(alpha = 0.10f)
                                else Color.Transparent,
                            )
                            .clickable { onResolutionSelected(preset) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.defaultResolution == preset,
                            onClick = { onResolutionSelected(preset) },
                            modifier = Modifier.size(28.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = QualityPurple,
                                unselectedColor = SecondaryText,
                            ),
                        )
                        Text(preset.label())
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                ReadOnlySetting("当前默认", settings.defaultResolution.label())
            }
        },
        SettingsContent(key = "download") {
            SettingsCard(
                title = "下载规则 / 任务行为",
                icon = Icons.Outlined.Download,
                accent = ForestGreen,
                tone = AppCardTone.MINT,
                compact = !expanded,
                modifier = Modifier.testTag("settings-download-card"),
            ) {
                SettingSwitchRow(
                    title = "恢复网络后自动继续",
                    summary = "仅恢复因网络中断而等待的任务，用户暂停的任务不会自动继续。",
                    checked = settings.autoResumeNetwork,
                    onCheckedChange = onAutoResumeChanged,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ActionSetting(
                    title = "同时下载",
                    value = "${settings.maxConcurrentDownloads} 个任务",
                    summary = "下一次开始下载时生效",
                    onClick = { concurrencyDialogVisible = true },
                    modifier = Modifier.testTag("settings-concurrency"),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ActionSetting(
                    title = "文件命名",
                    value = settings.fileNameRule.label(),
                    onClick = { namingDialogVisible = true },
                    modifier = Modifier.testTag("settings-file-name"),
                )
            }
        },
        SettingsContent(key = "storage") {
            SettingsCard(
                title = "保存位置 / 文件保存",
                icon = Icons.Outlined.Folder,
                accent = StorageOchre,
                tone = AppCardTone.OCHRE,
                compact = !expanded,
                modifier = Modifier.testTag("settings-storage-card"),
            ) {
                ActionSetting(
                    title = "下载路径",
                    value = settings.customTreeName ?: "系统媒体库",
                    summary = if (settings.customTreeUri == null) "视频 Movies；MP3 Music" else "已授权的自定义文件夹",
                    onClick = { storageDialogVisible = true },
                    modifier = Modifier.testTag("settings-storage-location"),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ActionSetting(
                    title = "导出单个平台 cookies.txt",
                    value = "选择账号与保存位置",
                    summary = "文件含登录凭据，请勿上传、转发或存入共享位置",
                    onClick = { exportSitePickerVisible = true },
                    modifier = Modifier.testTag("settings-export-cookies"),
                )
            }
        },
    )

    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-screen")
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings-expanded-grid"),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { SettingsHeader(expanded = true) }
                lazyGridItems(
                    items = settingsContent,
                    key = { it.key },
                    span = { GridItemSpan(if (it.spansFullWidth) maxLineSpan else 1) },
                ) { content ->
                    content.content()
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingsHeader(expanded = false)
            }
            lazyColumnItems(settingsContent, key = { it.key }) { content -> content.content() }
        }
    }
}

@Composable
private fun SettingsHeader(expanded: Boolean) {
    Text(
        "设置",
        style = if (expanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

private data class SettingsContent(
    val key: String,
    val spansFullWidth: Boolean = false,
    val content: @Composable () -> Unit,
)

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    tone: AppCardTone = AppCardTone.NEUTRAL,
    compact: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    WorkbenchCard(
        tone = tone,
        modifier = modifier,
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(if (compact) 22.dp else 24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = accent)
            }
            Spacer(Modifier.width(2.dp))
            content()
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
            OutlinedButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.width(58.dp).height(34.dp),
            ) { Text("清除", style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.width(8.dp))
        }
        Button(
            onClick = onOpen,
            enabled = state.site != SessionSite.XIAOHONGSHU,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.width(78.dp).height(34.dp),
        ) {
            Text(
                when (state.site) {
                    SessionSite.YOUTUBE -> if (state.hasSavedSession) "重新导入" else "导入"
                    SessionSite.XIAOHONGSHU -> "无需登录"
                    else -> when {
                        !state.hasSavedSession -> "登录"
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
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
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
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.widthIn(max = 220.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "打开${title}设置",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
