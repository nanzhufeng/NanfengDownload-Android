package com.nanzhufeng.videodownloader.feature.history

import android.annotation.SuppressLint
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nanzhufeng.videodownloader.domain.playback.VideoGesturePlayback
import com.nanzhufeng.videodownloader.domain.playback.VideoPlaybackMemory
import com.nanzhufeng.videodownloader.domain.playback.VideoPlayerGestureController
import com.nanzhufeng.videodownloader.domain.playback.VideoSeekFeedback
import com.nanzhufeng.videodownloader.domain.playback.VideoSeekSide
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("ClickableViewAccessibility") // Confirmed single taps invoke PlayerView.performClick; seek taps are consumed.
internal fun InternalVideoPlayerOverlay(
    playbackSessionId: String,
    title: String,
    videoUri: String,
    playbackMemory: VideoPlaybackMemory,
    segmentLabel: String? = null,
    initialPositionMillis: Long = 0L,
    initialPlayWhenReady: Boolean = true,
    onPlaybackSnapshot: (Long, Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var controlsVisible by remember(playbackSessionId, videoUri) { mutableStateOf(true) }
    var seekFeedback by remember(playbackSessionId, videoUri) { mutableStateOf<VideoSeekFeedback?>(null) }
    var feedbackGeneration by remember(playbackSessionId, videoUri) { mutableIntStateOf(0) }
    // Bind snapshots to this source. An old player's disposal must not call
    // the new source's callback and replace its freshly restored position.
    val snapshotCallback = remember(playbackSessionId, videoUri) { onPlaybackSnapshot }
    val player = remember(playbackSessionId, videoUri) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(VideoPlayerGestureController.STEP_MILLIS)
            .setSeekForwardIncrementMs(VideoPlayerGestureController.STEP_MILLIS)
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
                prepare()
                seekTo(initialPositionMillis.coerceAtLeast(0L))
                playWhenReady = initialPlayWhenReady
            }
    }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var bufferingVisible by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player, buffering) {
        bufferingVisible = false
        if (buffering) { delay(350); bufferingVisible = true }
    }
    var lastStoredPosition by remember(player) { androidx.compose.runtime.mutableLongStateOf(initialPositionMillis) }
    fun snapshot(flush: Boolean = false, explicitSeek: Boolean = false) {
        val position = player.currentPosition.coerceAtLeast(0L)
        snapshotCallback(position, player.playWhenReady)
        val ended = player.playbackState == Player.STATE_ENDED
        // A not-yet-ready or failed source at zero cannot erase a saved bookmark.
        if (player.playbackState == Player.STATE_IDLE ||
            (player.playbackState == Player.STATE_BUFFERING && position == 0L && initialPositionMillis > 0L && !explicitSeek)) return
        if (flush || ended || kotlin.math.abs(position - lastStoredPosition) >= 1000L) {
            if (!playbackMemory.save(videoUri, position, player.duration, ended, flush)) {
                android.util.Log.w("VideoPlayback", "Playback position could not be saved")
            }
            lastStoredPosition = if (ended) 0L else position
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) snapshot(flush = true)
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) snapshot(flush = true)
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) snapshot(flush = true, explicitSeek = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            snapshot(flush = true)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (isActive) {
            snapshot()
            delay(500)
        }
    }
    LaunchedEffect(feedbackGeneration) {
        if (seekFeedback != null) {
            delay(800)
            seekFeedback = null
        }
    }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).testTag("history-internal-video-player"),
    ) {
        key(playbackSessionId, videoUri) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        val playerView = this
                        this.player = player
                        useController = true
                        controllerAutoShow = false
                        controllerShowTimeoutMs = 2500
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                controlsVisible = visibility == View.VISIBLE
                            },
                        )
                        val gestures = VideoPlayerGestureController(object : VideoGesturePlayback {
                            override val positionMillis get() = player.currentPosition
                            override val durationMillis get() = player.duration
                            override var playWhenReady: Boolean
                                get() = player.playWhenReady
                                set(value) { player.playWhenReady = value }
                            override fun seekTo(positionMillis: Long) {
                                player.seekTo(positionMillis)
                                snapshot(flush = true, explicitSeek = true)
                            }
                            override fun hideControls() { playerView.hideController() }
                            override fun toggleControls() { playerView.performClick() }
                        })
                        fun feedback(value: VideoSeekFeedback?) {
                            if (value != null) { seekFeedback = value; feedbackGeneration++ }
                        }
                        fun side(x: Float) = if (x < width / 2f) VideoSeekSide.LEFT else VideoSeekSide.RIGHT
                        var latestX = 0f
                        var latestEventTime = 0L
                        var latestDownTime = 0L
                        val detector = GestureDetector(viewContext, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDown(event: MotionEvent): Boolean = true
                            override fun onDoubleTap(event: MotionEvent): Boolean {
                                feedback(gestures.doubleTap(side(latestX), latestEventTime, event.downTime, latestDownTime))
                                return true
                            }
                            override fun onDoubleTapEvent(event: MotionEvent): Boolean = true
                            override fun onSingleTapUp(event: MotionEvent): Boolean {
                                feedback(gestures.continuedTap(side(event.x), event.eventTime, event.downTime))
                                return true
                            }
                            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                                gestures.confirmedSingleTap(event.downTime)
                                return true
                            }
                        })
                        setOnTouchListener { _, event ->
                            latestX = event.x
                            latestEventTime = event.eventTime
                            latestDownTime = event.downTime
                            detector.onTouchEvent(event)
                            true
                        }
                        showController()
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            )
        }
        if (bufferingVisible) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(20.dp)
                    .semantics { contentDescription = "视频缓冲中" },
                color = Color.White.copy(alpha = 0.45f), strokeWidth = 1.5.dp,
            )
        }
        seekFeedback?.let { feedback ->
            val rewind = feedback.side == VideoSeekSide.LEFT
            Surface(
                modifier = Modifier.align(if (rewind) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        scaleX = 0.5f
                        scaleY = 0.5f
                        alpha = 0.5f
                        transformOrigin = TransformOrigin(if (rewind) 0f else 1f, 0.5f)
                    }
                    .testTag("history-video-seek-feedback"),
                shape = RoundedCornerShape(24.dp),
                // The whole pill uses 50% layer alpha: 0.6 * 0.5 gives a 30% opaque background.
                color = Color.Black.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (rewind) Icons.Outlined.FastRewind else Icons.Outlined.FastForward,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("${if (rewind) "快退" else "快进"} ${feedback.seconds} 秒", color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
        if (controlsVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                    .testTag("history-internal-video-title-controls"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(listOfNotNull(title, segmentLabel).joinToString(" · "), color = Color.White,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭视频播放", tint = Color.White)
                }
            }
        }
    }
}
