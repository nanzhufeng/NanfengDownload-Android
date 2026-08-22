package com.nanzhufeng.videodownloader.feature.history

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
internal fun InternalImageGalleryDialog(
    title: String,
    imageUris: List<String>,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pages = remember(imageUris) {
        buildHistoryGalleryPages(
            imageUris.map { uri ->
                HistoryGalleryMedia(
                    uri = uri,
                    mimeType = context.contentResolver.getType(Uri.parse(uri)).orEmpty(),
                )
            },
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                when (val page = pages[index]) {
                    is HistoryGalleryPage.Image -> AsyncImage(
                        model = page.uri,
                        contentDescription = "$title 第 ${index + 1} 张，共 ${pages.size} 张",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is HistoryGalleryPage.LivePhoto -> LivePhotoPage(
                        stillUri = page.stillUri,
                        motionUri = page.motionUri,
                        contentDescription = "$title 第 ${index + 1} 张动态图片，共 ${pages.size} 张",
                        active = pagerState.currentPage == index,
                    )

                    is HistoryGalleryPage.Video -> GalleryVideoPage(
                        uri = page.uri,
                        description = "$title 第 ${index + 1} 个视频，共 ${pages.size} 项",
                        active = pagerState.currentPage == index,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$title  ·  ${pagerState.currentPage + 1}/${pages.size}",
                    color = Color.White,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭图片查看", tint = Color.White)
                }
            }
        }
    }
}

internal data class HistoryGalleryMedia(val uri: String, val mimeType: String)

internal sealed interface HistoryGalleryPage {
    data class Image(val uri: String) : HistoryGalleryPage
    data class LivePhoto(val stillUri: String, val motionUri: String) : HistoryGalleryPage
    data class Video(val uri: String) : HistoryGalleryPage
}

internal fun buildHistoryGalleryPages(media: List<HistoryGalleryMedia>): List<HistoryGalleryPage> = buildList {
    var index = 0
    while (index < media.size) {
        val current = media[index]
        val next = media.getOrNull(index + 1)
        if (current.mimeType.startsWith("image/") && next?.mimeType?.startsWith("video/") == true) {
            add(HistoryGalleryPage.LivePhoto(current.uri, next.uri))
            index += 2
        } else if (current.mimeType.startsWith("image/")) {
            add(HistoryGalleryPage.Image(current.uri))
            index += 1
        } else {
            add(HistoryGalleryPage.Video(current.uri))
            index += 1
        }
    }
}

@Composable
private fun LivePhotoPage(
    stillUri: String,
    motionUri: String,
    contentDescription: String,
    active: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = stillUri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        GalleryVideoPage(motionUri, contentDescription, active)
    }
}

@Composable
private fun GalleryVideoPage(
    uri: String,
    description: String,
    active: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose(exoPlayer::release)
    }
    LaunchedEffect(active, exoPlayer) {
        exoPlayer.playWhenReady = active
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                player = exoPlayer
                useController = false
                contentDescription = description
            }
        },
        update = { it.player = exoPlayer },
        modifier = Modifier.fillMaxSize(),
    )
}
