package com.nanzhufeng.videodownloader.feature.history

import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
internal fun InternalVideoPlayerOverlay(
    playbackSessionId: String,
    title: String,
    videoUri: String,
    segmentLabel: String? = null,
    initialPositionMillis: Long = 0L,
    initialPlayWhenReady: Boolean = true,
    onPlaybackSnapshot: (Long, Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var controlsVisible by remember(playbackSessionId, videoUri) { mutableStateOf(true) }
    val player = remember(playbackSessionId, videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            prepare()
            seekTo(initialPositionMillis.coerceAtLeast(0L))
            playWhenReady = initialPlayWhenReady
        }
    }
    DisposableEffect(player) {
        onDispose {
            onPlaybackSnapshot(player.currentPosition, player.playWhenReady)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (isActive) {
            onPlaybackSnapshot(player.currentPosition, player.playWhenReady)
            delay(500)
        }
    }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("history-internal-video-player"),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        },
                    )
                    val doubleTapDetector = GestureDetector(
                        viewContext,
                        object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDown(event: MotionEvent): Boolean = true

                            override fun onDoubleTap(event: MotionEvent): Boolean {
                                if (player.playWhenReady) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                                onPlaybackSnapshot(player.currentPosition, player.playWhenReady)
                                return true
                            }
                        },
                    )
                    setOnTouchListener { _, event ->
                        doubleTapDetector.onTouchEvent(event)
                        false
                    }
                }
            },
            update = { it.player = player },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        )
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                    .testTag("history-internal-video-title-controls"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOfNotNull(title, segmentLabel).joinToString(" · "),
                    color = Color.White,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭视频播放", tint = Color.White)
                }
            }
        }
    }
}
