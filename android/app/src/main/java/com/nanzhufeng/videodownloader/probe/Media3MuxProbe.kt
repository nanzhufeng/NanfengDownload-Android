package com.nanzhufeng.videodownloader.probe

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object Media3MuxProbe {
    fun declaredTrackTypes(): Set<Int> = setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)

    suspend fun merge(
        context: Context,
        video: File,
        audio: File,
        output: File,
    ): File = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            output.parentFile?.mkdirs()
            output.delete()

            val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(video)))
                .setRemoveAudio(true)
                .build()
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audio)))
                .setRemoveVideo(true)
                .build()
            val videoSequence = EditedMediaItemSequence.Builder(listOf(videoItem)).build()
            val audioSequence = EditedMediaItemSequence.Builder(listOf(audioItem)).build()
            val composition = Composition.Builder(videoSequence, audioSequence)
                .setTransmuxVideo(true)
                .setTransmuxAudio(true)
                .build()

            val transformer = Transformer.Builder(context)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            result: ExportResult,
                        ) {
                            if (continuation.isActive) continuation.resume(output)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }
                    },
                )
                .build()

            continuation.invokeOnCancellation { transformer.cancel() }
            transformer.start(composition, output.absolutePath)
        }
    }
}
