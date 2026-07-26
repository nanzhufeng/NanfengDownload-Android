package com.nanzhufeng.videodownloader.feature.history

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import kotlinx.coroutines.delay

@Composable
internal fun InternalAudioPlayerDialog(
    item: DownloadHistory,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uri = remember(item.outputUri) {
        item.outputUri?.let(Uri::parse)
    }
    var prepared by remember(item.taskId) { mutableStateOf(false) }
    var playing by remember(item.taskId) { mutableStateOf(false) }
    var durationMillis by remember(item.taskId) { mutableIntStateOf(0) }
    var positionMillis by remember(item.taskId) { mutableIntStateOf(0) }
    var errorMessage by remember(item.taskId) { mutableStateOf<String?>(null) }
    val player = remember(item.taskId, item.outputUri) { MediaPlayer() }

    DisposableEffect(player, uri) {
        if (uri == null) {
            errorMessage = "该音频文件已不可用，请确认文件没有被移动或删除。"
        } else {
            runCatching {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                player.setDataSource(context, uri)
                player.setOnPreparedListener {
                    prepared = true
                    durationMillis = it.duration.coerceAtLeast(0)
                    it.start()
                    playing = true
                }
                player.setOnCompletionListener {
                    playing = false
                    positionMillis = durationMillis
                }
                player.setOnErrorListener { _, _, _ ->
                    prepared = false
                    playing = false
                    errorMessage = "内置播放器无法读取该音频。请确认文件未损坏，或在更多菜单中选择外部播放器。"
                    true
                }
                player.prepareAsync()
            }.onFailure {
                errorMessage = "内置播放器无法打开该音频。请确认文件仍然存在，或在更多菜单中选择外部播放器。"
            }
        }
        onDispose {
            runCatching { player.stop() }
            player.reset()
            player.release()
        }
    }

    LaunchedEffect(player, prepared, playing) {
        while (prepared && playing) {
            positionMillis = runCatching { player.currentPosition }.getOrDefault(positionMillis)
            delay(250L)
        }
    }

    AlertDialog(
        modifier = Modifier.testTag("internal-audio-player"),
        onDismissRequest = onDismiss,
        title = { Text("正在播放音频") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.creator,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Slider(
                    value = positionMillis.coerceIn(0, durationMillis.coerceAtLeast(1)).toFloat(),
                    onValueChange = { value ->
                        positionMillis = value.toInt()
                        if (prepared) runCatching { player.seekTo(positionMillis) }
                    },
                    valueRange = 0f..durationMillis.coerceAtLeast(1).toFloat(),
                    enabled = prepared && durationMillis > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatPlayerTime(positionMillis))
                    IconButton(
                        enabled = prepared,
                        onClick = {
                            if (playing) {
                                player.pause()
                                playing = false
                            } else {
                                if (positionMillis >= durationMillis && durationMillis > 0) {
                                    player.seekTo(0)
                                    positionMillis = 0
                                }
                                player.start()
                                playing = true
                            }
                        },
                    ) {
                        Icon(
                            if (playing) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = if (playing) "暂停" else "播放",
                        )
                    }
                    Text(formatPlayerTime(durationMillis))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun formatPlayerTime(valueMillis: Int): String {
    val totalSeconds = valueMillis.coerceAtLeast(0) / 1_000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
