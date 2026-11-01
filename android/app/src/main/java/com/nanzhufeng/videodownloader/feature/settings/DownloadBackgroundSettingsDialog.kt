package com.nanzhufeng.videodownloader.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.domain.download.DownloadIslandWindows
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun DownloadBackgroundSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val topConnected by DownloadIslandWindows.topConnected.collectAsState()
    var showTopPermission by rememberSaveable { mutableStateOf(false) }
    var unrestricted by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        overlay = Settings.canDrawOverlays(context)
        unrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    DisposableEffect(owner) {
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) {
        error = null
        runCatching { launcher.launch(intent) }
            .onFailure { error = "无法打开系统页面，请到系统设置手动调整" }
    }
    if (showTopPermission) IslandTopPermissionDialog { showTopPermission = false }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 20.dp)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .testTag("background-settings-dialog"),
            shape = RoundedCornerShape(28.dp), color = Color.White, tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    Text("后台下载与灵动岛", Modifier.padding(horizontal = 48.dp),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = onDismiss, Modifier.align(Alignment.CenterEnd).size(48.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭后台下载设置")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BackgroundSettingsEntry(
                        title = "顶部灵动岛", status = when {
                            topConnected -> "置顶已授权"
                            overlay -> "普通悬浮（未置顶）"
                            else -> "未开启"
                        },
                        icon = Icons.Outlined.PictureInPicture, tag = "background-overlay-entry",
                        onClick = { showTopPermission = true },
                    )
                    BackgroundSettingsEntry(
                        title = "后台电池", status = if (unrestricted) "不受限制" else "系统优化中",
                        icon = Icons.Outlined.BatteryFull, tag = "background-battery-entry",
                        onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    )
                    BackgroundSettingsEntry(
                        title = "下载通知", status = if (notifications) "已开启" else "未开启",
                        icon = Icons.Outlined.Notifications, tag = "background-notifications-entry",
                        onClick = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun BackgroundSettingsEntry(
    title: String, status: String, icon: ImageVector, tag: String, onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag),
        color = Color(0xFFF7F7F7), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** The system switch belongs to the user. This dialog never enables services itself. */
@Composable
private fun IslandTopPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val connected by DownloadIslandWindows.topConnected.collectAsState()
    val initiallyConnected = remember { connected }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    LaunchedEffect(connected) { if (connected && !initiallyConnected) onDismiss() }
    fun open(intent: Intent) {
        error = null
        runCatching { launcher.launch(intent) }
            .onFailure { error = "无法打开系统页面，请在系统设置中手动开启" }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, tonalElevation = 0.dp,
        title = { Text("灵动岛置顶") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (connected) "可在系统无障碍设置中管理“南枫下载灵动岛置顶”。"
                else "在系统无障碍设置中开启“南枫下载灵动岛置顶”，使下载岛显示在状态栏前方。")
            Text("此服务仅绘制下载进度，不读取屏幕内容，不执行点击、输入或手势。")
            TextButton(onClick = {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }) { Text("普通悬浮权限") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
            Text(if (connected) "管理系统授权" else "前往系统授权")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (connected) "关闭" else "稍后") } },
    )
}

/** Ask once from the existing start action; permission never gates the real download. */
@Composable
internal fun rememberIslandAwareDownloadStart(onStart: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("download_island_guidance", android.content.Context.MODE_PRIVATE) }
    val topConnected by DownloadIslandWindows.topConnected.collectAsState()
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    if (showPrompt) IslandTopPermissionDialog { showPrompt = false }
    return {
        onStart()
        if (!topConnected && !preferences.getBoolean("top_shown", false)) {
            preferences.edit().putBoolean("top_shown", true).apply()
            showPrompt = true
        }
    }
}
