package com.nanzhufeng.videodownloader.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.ui.AppCardTone
import com.nanzhufeng.videodownloader.core.ui.PlatformIcon
import com.nanzhufeng.videodownloader.core.ui.WorkbenchCard
import com.nanzhufeng.videodownloader.data.settings.AppSettings
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
    expanded: Boolean,
) {
    val cookiePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.toString()?.let(onImportYoutubeCookies)
    }
    val stateFor: (SessionSite) -> SiteSessionState = { site ->
        sessions.firstOrNull { it.site == site }
            ?: SiteSessionState(site, false, "未保存登录会话")
    }
    val youtube = stateFor(SessionSite.YOUTUBE)
    val douyin = stateFor(SessionSite.DOUYIN)
    val tiktok = stateFor(SessionSite.TIKTOK)
    val settingsContent = listOf(
        SettingsContent(key = "account", spansFullWidth = true) {
            SettingsCard(title = "账号与权限") {
                SessionRow(
                    state = youtube,
                    modifier = Modifier.testTag("settings-youtube"),
                    onOpen = { cookiePicker.launch(arrayOf("text/plain", "application/octet-stream")) },
                    onClear = { onClearSession(SessionSite.YOUTUBE) },
                )
                HorizontalDivider()
                Column(
                    modifier = Modifier.testTag("settings-short-video-platforms"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("短视频平台", fontWeight = FontWeight.Medium)
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
                }
            }
        },
        SettingsContent(key = "quality") {
            SettingsCard(
                title = "默认分辨率",
                tone = AppCardTone.PURPLE,
                modifier = Modifier.testTag("settings-quality-card"),
            ) {
                ResolutionPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResolutionSelected(preset) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.defaultResolution == preset,
                            onClick = { onResolutionSelected(preset) },
                        )
                        Text(preset.label())
                    }
                }
            }
        },
        SettingsContent(key = "download") {
            SettingsCard(
                title = "下载规则",
                tone = AppCardTone.MINT,
                modifier = Modifier.testTag("settings-download-card"),
            ) {
                SettingSwitchRow(
                    title = "恢复网络后自动继续",
                    summary = "仅恢复因网络中断而等待的任务，用户暂停的任务不会自动继续。",
                    checked = settings.autoResumeNetwork,
                    onCheckedChange = onAutoResumeChanged,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ReadOnlySetting("同时下载", "1 个任务，保证手机温度和文件合并稳定")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ReadOnlySetting("文件命名", "发布日期 + 视频标题")
            }
        },
        SettingsContent(key = "storage") {
            SettingsCard(
                title = "保存位置",
                tone = AppCardTone.OCHRE,
                modifier = Modifier.testTag("settings-storage-card"),
            ) {
                Text(
                    "视频：Movies/南烛枫视频下载器；MP3：Music/南烛枫视频下载器",
                    fontWeight = FontWeight.Medium,
                )
                Text("使用 Android 公共媒体目录，卸载应用后已下载文件仍会保留。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        SettingsContent(key = "content") {
            SettingsCard(title = "内容范围", tone = AppCardTone.ORANGE) {
                Text(
                    "支持公开内容和登录后当前账号有权限访问的内容。不绕过会员、付费、DRM 或私密限制。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                item(span = { GridItemSpan(maxLineSpan) }) { SettingsHeader() }
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsHeader()
            }
            lazyColumnItems(settingsContent, key = { it.key }) { content -> content.content() }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Column {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("默认规则用于之后新加入的任务，登录会话保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class SettingsContent(
    val key: String,
    val spansFullWidth: Boolean = false,
    val content: @Composable () -> Unit,
)

@Composable
private fun SettingsCard(
    title: String,
    tone: AppCardTone = AppCardTone.NEUTRAL,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    WorkbenchCard(tone = tone, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformIcon(platform = state.site.platform(), contentDescription = "${state.site.label} 图标")
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.site.label, fontWeight = FontWeight.Medium)
            Text(state.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (state.hasSavedSession) {
            OutlinedButton(onClick = onClear) { Text("清除") }
            Spacer(Modifier.width(8.dp))
        }
        Button(onClick = onOpen) {
            Text(
                when (state.site) {
                    SessionSite.YOUTUBE -> if (state.hasSavedSession) "重新导入" else "导入 cookies.txt"
                    else -> if (state.hasSavedSession) "重新登录" else "登录"
                },
            )
        }
    }
}

private fun SessionSite.platform(): DownloadPlatform = when (this) {
    SessionSite.YOUTUBE -> DownloadPlatform.YOUTUBE
    SessionSite.DOUYIN -> DownloadPlatform.DOUYIN
    SessionSite.TIKTOK -> DownloadPlatform.TIKTOK
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReadOnlySetting(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ResolutionPreset.label(): String = when (this) {
    ResolutionPreset.BEST -> "最佳画质"
    ResolutionPreset.UP_TO_1080P -> "1080p 及以下"
    ResolutionPreset.UP_TO_720P -> "720p 及以下"
    ResolutionPreset.AUDIO_MP3 -> "仅音频 MP3"
}
