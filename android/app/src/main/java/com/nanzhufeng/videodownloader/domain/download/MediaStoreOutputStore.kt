package com.nanzhufeng.videodownloader.domain.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.MediaFileValidator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreOutputStore(
    context: Context,
    private val policy: OutputFilePolicy = OutputFilePolicy(),
) : DownloadOutputStore {
    private val resolver = context.applicationContext.contentResolver
    private var cachedIndex: List<IndexedMedia>? = null

    override suspend fun findExisting(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): StoredMedia? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val path = policy.relativePath(media, resolution)
        val candidate = loadIndex().firstOrNull { item -> item.relativePath == path }
            ?: return@withContext null
        candidate.stored.takeIf(::isValid)
    }

    override suspend fun uriExists(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                descriptor.statSize >= MIN_MEDIA_BYTES
            } == true
        }.getOrDefault(false)
    }

    override suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
    ): StoredMedia = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "正式公共目录写入要求 Android 10 或更高版本"
        }
        require(MediaFileValidator.isLikelyMedia(prepared.file)) { "待保存文件不是有效媒体" }

        val relativePath = policy.relativePath(media, resolution)
        val directory = relativePath.substringBeforeLast('/') + "/"
        val displayName = relativePath.substringAfterLast('/')
        val collection = collectionFor(resolution)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, prepared.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(collection, values)) {
            "无法在系统媒体库创建输出文件"
        }
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")) { "无法打开系统媒体库输出流" }
                .use { output -> prepared.file.inputStream().use { input -> input.copyTo(output) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            val stored = StoredMedia(uri.toString(), prepared.file.length())
            require(isValid(stored)) { "写入系统媒体库后校验失败" }
            cachedIndex = cachedIndex.orEmpty() + IndexedMedia(
                relativePath = relativePath,
                stored = stored,
            )
            prepared.file.delete()
            stored
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun loadIndex(): List<IndexedMedia> = cachedIndex ?: buildList {
        addAll(queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
        addAll(queryCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI))
    }.also { cachedIndex = it }

    private fun queryCollection(collection: Uri): List<IndexedMedia> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("Movies/南烛枫视频下载器/%")
        return resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            buildList {
                while (cursor.moveToNext()) {
                    val size = cursor.getLong(sizeIndex)
                    if (size < MIN_MEDIA_BYTES) continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    val directory = cursor.getString(pathIndex).orEmpty().trimEnd('/')
                    val name = cursor.getString(nameIndex).orEmpty()
                    add(
                        IndexedMedia(
                            relativePath = "$directory/$name",
                            stored = StoredMedia(uri.toString(), size),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun isValid(stored: StoredMedia): Boolean = runCatching {
        if (stored.fileSize < MIN_MEDIA_BYTES) return@runCatching false
        val uri = Uri.parse(stored.uri)
        requireNotNull(resolver.openInputStream(uri)).use { input ->
            MediaFileValidator.isLikelyMedia(input, stored.fileSize)
        }
    }.getOrDefault(false)

    private fun collectionFor(resolution: ResolutionPreset): Uri =
        if (resolution == ResolutionPreset.AUDIO_MP3) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private data class IndexedMedia(
        val relativePath: String,
        val stored: StoredMedia,
    )

    private companion object {
        const val MIN_MEDIA_BYTES = 64 * 1024L
    }
}
