package com.nanzhufeng.videodownloader.feature.history

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MediaPlayerOption(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemDefault: Boolean,
)

internal sealed interface MediaOpenResult {
    data object Opened : MediaOpenResult
    data object MissingMedia : MediaOpenResult
    data object NoPlayer : MediaOpenResult
    data object CannotOpen : MediaOpenResult
}

internal fun openWithDefaultPlayer(context: Context, item: DownloadHistory): MediaOpenResult =
    openMedia(context, item) { intent -> context.startActivity(intent) }

internal fun shouldUseInternalAudioPlayer(item: DownloadHistory): Boolean =
    item.resolution == ResolutionPreset.AUDIO_MP3

internal fun openWithPlayer(
    context: Context,
    item: DownloadHistory,
    option: MediaPlayerOption,
): MediaOpenResult = openMedia(context, item) { intent ->
    context.startActivity(
        intent.apply {
            component = ComponentName(option.packageName, option.activityName)
        },
    )
}

internal fun mediaOpenMessage(result: MediaOpenResult, isAudio: Boolean): String {
    val mediaName = if (isAudio) "音频" else "视频"
    return when (result) {
        MediaOpenResult.Opened -> ""
        MediaOpenResult.MissingMedia -> "${mediaName}文件不存在或已无法读取。已在历史中标记，可重新读取原链接下载。"
        MediaOpenResult.NoPlayer -> "没有可用的${mediaName}播放器。请安装或启用一个支持该文件的播放器后重试。"
        MediaOpenResult.CannotOpen -> "${mediaName}文件暂时无法打开。请确认文件未损坏后重试。"
    }
}

private fun openMedia(
    context: Context,
    item: DownloadHistory,
    start: (Intent) -> Unit,
): MediaOpenResult {
    if (!isMediaReadable(context, item)) return MediaOpenResult.MissingMedia
    val intent = runCatching { mediaViewIntent(context, item) }
        .getOrElse { return MediaOpenResult.CannotOpen }
    return try {
        start(intent)
        MediaOpenResult.Opened
    } catch (_: ActivityNotFoundException) {
        MediaOpenResult.NoPlayer
    } catch (_: FileNotFoundException) {
        // The file may have been deleted after the preflight completed.
        MediaOpenResult.MissingMedia
    } catch (_: SecurityException) {
        MediaOpenResult.CannotOpen
    } catch (_: Exception) {
        MediaOpenResult.CannotOpen
    }
}

internal fun isMediaReadable(context: Context, item: DownloadHistory): Boolean {
    val uri = item.outputUri?.let(Uri::parse) ?: return false
    return isReadableMediaUri(context, uri)
}

internal suspend fun isMediaReadableInBackground(context: Context, item: DownloadHistory): Boolean =
    withContext(Dispatchers.IO) { isMediaReadable(context, item) }

private fun isReadableMediaUri(context: Context, uri: Uri): Boolean = runCatching {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        // -1 means an unknown stream length, which is still readable. A zero-length
        // completed media file is never a valid output from this app.
        descriptor.statSize != 0L
    } == true
}.getOrDefault(false)

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
