package com.nanzhufeng.videodownloader.feature.history

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset

internal data class MediaPlayerOption(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemDefault: Boolean,
)

internal fun openWithDefaultPlayer(context: Context, item: DownloadHistory): Result<Unit> = runCatching {
    context.startActivity(mediaViewIntent(context, item))
}

internal fun shouldUseInternalAudioPlayer(item: DownloadHistory): Boolean =
    item.resolution == ResolutionPreset.AUDIO_MP3

internal fun openWithPlayer(
    context: Context,
    item: DownloadHistory,
    option: MediaPlayerOption,
): Result<Unit> = runCatching {
    context.startActivity(
        mediaViewIntent(context, item).apply {
            component = ComponentName(option.packageName, option.activityName)
        },
    )
}

internal fun queryMediaPlayers(context: Context, item: DownloadHistory): List<MediaPlayerOption> {
    val intent = mediaViewIntent(context, item)
    val packageManager = context.packageManager
    val defaultComponent = packageManager.resolveActivity(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.let { ComponentName(it.packageName, it.name) }

    @Suppress("DEPRECATION")
    return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            val component = ComponentName(activity.packageName, activity.name)
            val isDefault = component == defaultComponent
            val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
            } else {
                ApplicationInfo.CATEGORY_UNDEFINED
            }
            if (!isLikelyMediaPlayer(activity.packageName, activity.name, category, isDefault)) {
                return@mapNotNull null
            }
            MediaPlayerOption(
                packageName = activity.packageName,
                activityName = activity.name,
                label = resolved.loadLabel(packageManager).toString().ifBlank { activity.packageName },
                isSystemDefault = isDefault,
            )
        }
        .distinctBy { "${it.packageName}/${it.activityName}" }
        .sortedWith(compareByDescending<MediaPlayerOption> { it.isSystemDefault }.thenBy { it.label })
}

internal fun isLikelyMediaPlayer(
    packageName: String,
    activityName: String,
    applicationCategory: Int,
    isSystemDefault: Boolean,
): Boolean {
    if (isSystemDefault) return true
    if (applicationCategory == ApplicationInfo.CATEGORY_VIDEO ||
        applicationCategory == ApplicationInfo.CATEGORY_AUDIO
    ) {
        return true
    }
    val identity = "$packageName $activityName".lowercase()
    return PLAYER_IDENTITY_MARKERS.any(identity::contains)
}

private fun mediaViewIntent(context: Context, item: DownloadHistory): Intent {
    val uri = requireNotNull(item.outputUri?.let(Uri::parse)) { "该记录没有可播放文件" }
    val resolvedType = context.contentResolver.getType(uri)
        ?.takeIf { it.startsWith("video/") || it.startsWith("audio/") }
    val mimeType = resolvedType ?: if (item.resolution == ResolutionPreset.AUDIO_MP3) {
        "audio/mpeg"
    } else {
        "video/mp4"
    }
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "下载文件", uri)
    }
}

private val PLAYER_IDENTITY_MARKERS = listOf(
    "videolan",
    "vlc",
    "mxtech",
    "mpv",
    "kodi",
    "kmplayer",
    "nplayer",
    "novaplayer",
    "potplayer",
    "videoplayer",
    "video.ui",
    "coloros.video",
    "heytap.yoli",
)
