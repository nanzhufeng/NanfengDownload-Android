package com.nanzhufeng.videodownloader.domain.download

import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.domain.download.audio.LameMp3Encoder
import com.nanzhufeng.videodownloader.domain.download.audio.PcmFormat
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreOutputStoreInstrumentedTest {
    @Test
    fun publishesMp3UnderSharedMusicDirectory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "media-store-audio-test.mp3")
        source.delete()
        encodeFourSecondsOfSilence(source)
        assertTrue(source.length() > 64 * 1_024L)
        var publishedUri: Uri? = null

        try {
            val stored = MediaStoreOutputStore(context).publish(
                media = media(),
                resolution = ResolutionPreset.AUDIO_MP3,
                prepared = PreparedMedia(source, "audio/mpeg"),
            )
            publishedUri = Uri.parse(stored.uri)

            context.contentResolver.query(
                publishedUri,
                arrayOf(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "Music/南烛枫视频下载器/YouTube/测试作者/",
                    cursor.getString(0),
                )
                assertTrue(cursor.getString(1).endsWith(".mp3"))
            }
        } finally {
            publishedUri?.let { context.contentResolver.delete(it, null, null) }
            source.delete()
        }
    }

    @Test
    fun publishesSegmentedMp3FilesAsOneStoredMediaResult() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sources = (1..2).map { index ->
            File(context.cacheDir, "media-store-segment-$index.mp3").also {
                it.delete()
                encodeFourSecondsOfSilence(it)
            }
        }
        val expectedBytes = sources.sumOf(File::length)
        val publishedUris = mutableListOf<Uri>()

        try {
            val stored = MediaStoreOutputStore(context).publish(
                media = media().copy(
                    contentId = "media-store-segmented-audio-test",
                    title = "MediaStore 分段音频测试",
                ),
                resolution = ResolutionPreset.AUDIO_MP3,
                prepared = PreparedMedia(
                    file = sources.first(),
                    mimeType = "audio/mpeg",
                    additionalFiles = sources.drop(1),
                ),
                saveTreeUri = null,
                fileNameRule = FileNameRule.DATE_AND_TITLE,
                audioSegmentCount = 2,
            )
            publishedUris += stored.uris.map(Uri::parse)

            assertEquals(2, stored.uris.size)
            assertEquals(expectedBytes, stored.fileSize)
            val displayNames = publishedUris.map { uri ->
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getString(0)
                }
            }
            assertTrue(displayNames[0].contains("第01段，共2段"))
            assertTrue(displayNames[1].contains("第02段，共2段"))
        } finally {
            publishedUris.forEach { context.contentResolver.delete(it, null, null) }
            sources.forEach(File::delete)
        }
    }

    @Test
    fun publishesSegmentedVideoFilesAsOneStoredMediaResult() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val sources = (1..2).map { index ->
            File(context.cacheDir, "media-store-video-segment-$index.mp4").also { destination ->
                instrumentation.context.assets.open("video/segment-source-6s.mp4").use { input ->
                    destination.outputStream().use(input::copyTo)
                }
            }
        }
        val expectedBytes = sources.sumOf(File::length)
        val publishedUris = mutableListOf<Uri>()

        try {
            val stored = MediaStoreOutputStore(context).publish(
                media = media().copy(
                    contentId = "media-store-segmented-video-test",
                    title = "MediaStore 分段视频测试",
                ),
                resolution = ResolutionPreset.UP_TO_720P,
                prepared = PreparedMedia(
                    file = sources.first(),
                    mimeType = "video/mp4",
                    additionalFiles = sources.drop(1),
                ),
                saveTreeUri = null,
                fileNameRule = FileNameRule.DATE_AND_TITLE,
                audioSegmentCount = 2,
            )
            publishedUris += stored.uris.map(Uri::parse)

            assertEquals(2, stored.uris.size)
            assertEquals(expectedBytes, stored.fileSize)
            val displayNames = publishedUris.map { uri ->
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getString(0)
                }
            }
            assertTrue(displayNames[0].contains("第01段，共2段"))
            assertTrue(displayNames[1].contains("第02段，共2段"))
        } finally {
            publishedUris.forEach { context.contentResolver.delete(it, null, null) }
            sources.forEach(File::delete)
        }
    }

    private fun encodeFourSecondsOfSilence(destination: File) {
        val sampleRate = 44_100
        val channelCount = 2
        LameMp3Encoder().open(destination, PcmFormat(sampleRate, channelCount)).use { session ->
            var remainingFrames = sampleRate * 4
            while (remainingFrames > 0) {
                val frames = minOf(1_152, remainingFrames)
                session.encode(ShortArray(frames * channelCount), frames)
                remainingFrames -= frames
            }
            session.finish()
        }
    }

    private fun media() = MediaItem(
        mediaKey = "youtube:media-store-audio-test",
        platform = DownloadPlatform.YOUTUBE,
        contentId = "media-store-audio-test",
        originalUrl = "https://example.invalid/media-store-audio-test",
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = "MediaStore 音频测试",
        creator = "测试作者",
        creatorId = "test-author",
        publishDate = "20260716",
        thumbnailUrl = "",
    )
}
