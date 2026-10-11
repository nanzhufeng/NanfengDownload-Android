package com.nanzhufeng.videodownloader.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun DownloadBackgroundSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var unrestricted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        overlay = Settings.canDrawOverlays(context)
        unrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    DisposableEffect(owner) {
        refresh()
        val observer=LifecycleEventObserver { _, event -> if(event==Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) { runCatching { launcher.launch(intent) }.onFailure { error="无法打开系统页面，请到系统设置手动调整" } }
    AlertDialog(onDismissRequest=onDismiss, containerColor=Color.White, tonalElevation=0.dp,
        title={ Text("后台下载与灵动岛") },
        text={ Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(overlay) "顶部灵动岛：已授权" else "顶部灵动岛：待授权")
            Text("离开软件后显示真实下载进度和速度；点击展开，查看任务或停止下载。合并、校验与保存会分别显示，保存成功后才显示完成。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))}) {
                Text(if(overlay) "管理灵动岛权限" else "允许显示顶部灵动岛")
            }
            Text("未授权时仍通过下载通知提醒；锁屏不显示悬浮窗。",style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(if(unrestricted) "电池策略：已允许不受限制" else "电池策略：系统优化可能影响长下载")
            TextButton(onClick={open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}) { Text("设置后台电池策略") }
            TextButton(onClick={open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) { Text("管理下载通知") }
            Text("后台下载使用前台任务与已保存断点。系统强制停止、关机或厂商限制仍可能中断；重新打开后可继续。",style=MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        } }, confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})
}

/** Ask at the existing start action once; consent never gates the real download. */
@Composable
internal fun rememberIslandAwareDownloadStart(onStart: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("download_island_guidance", android.content.Context.MODE_PRIVATE) }
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) showPrompt = false
    }
    if (showPrompt) AlertDialog(
        onDismissRequest = { showPrompt = false }, containerColor = Color.White, tonalElevation = 0.dp,
        title = { Text("开启后台灵动岛") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("下载已经开始。允许显示在其他应用上层后，离开南枫下载也能看到进度和速度。")
            Text("在系统列表中选择“南枫下载”并开启权限。灵动岛默认简洁版，点击展开详情，点击标题收起。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { launcher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))) }
                .onFailure { error = "无法打开系统页面，请到设置中的“后台下载与灵动岛”开启" }
        }) { Text("开启灵动岛") } },
        dismissButton = { TextButton(onClick = { showPrompt = false }) { Text("稍后") } },
    )
    return {
        onStart()
        if (!Settings.canDrawOverlays(context) && !preferences.getBoolean("shown", false)) {
            preferences.edit().putBoolean("shown", true).apply()
            showPrompt = true
        }
    }
}
