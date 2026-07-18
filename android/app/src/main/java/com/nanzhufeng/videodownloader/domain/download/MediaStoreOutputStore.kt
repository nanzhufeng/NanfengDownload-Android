package com.nanzhufeng.videodownloader.domain.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import com.nanzhufeng.videodownloader.probe.MediaFileValidator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaStoreOutputStore(
    context: Context,
    private val policy: OutputFilePolicy = OutputFilePolicy(),
) : DownloadOutputStore {
    private val resolver = context.applicationContext.contentResolver
    private val outputMutex = Mutex()
    private var cachedIndex: List<IndexedMedia>? = null

    override suspend fun findExisting(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): StoredMedia? = findExisting(media, resolution, null, FileNameRule.DATE_AND_TITLE)

    override suspend fun findExisting(
        media: MediaItem,
        resolution: ResolutionPreset,
        saveTreeUri: String?,
        fileNameRule: FileNameRule,
    ): StoredMedia? = outputMutex.withLock { withContext(Dispatchers.IO) {
        if (saveTreeUri != null) {
            return@withContext findTreeDocument(
                treeUri = Uri.parse(saveTreeUri),
                relativePath = customRelativePath(media, resolution, fileNameRule),
            )?.let { uri ->
                val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                StoredMedia(uri.toString(), size).takeIf(::isValid)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val path = policy.relativePath(media, resolution, fileNameRule)
        val candidate = loadIndex().firstOrNull { item -> item.relativePath == path }
            ?: return@withContext null
        candidate.stored.takeIf(::isValid)
    } }

    override suspend fun uriExists(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                val size = descriptor.statSize
                if (size < MIN_MP3_BYTES) return@use false
                requireNotNull(resolver.openInputStream(Uri.parse(uri))).use { input ->
                    MediaFileValidator.isLikelyMedia(input, size)
                }
            } == true
        }.getOrDefault(false)
    }

    override suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
    ): StoredMedia = publish(media, resolution, prepared, null, FileNameRule.DATE_AND_TITLE)

    override suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
        saveTreeUri: String?,
        fileNameRule: FileNameRule,
    ): StoredMedia = outputMutex.withLock { withContext(Dispatchers.IO) {
        if (saveTreeUri != null) {
            return@withContext publishToTree(
                treeUri = Uri.parse(saveTreeUri),
                relativePath = customRelativePath(media, resolution, fileNameRule),
                prepared = prepared,
            )
        }
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "正式公共目录写入要求 Android 10 或更高版本"
        }
        require(MediaFileValidator.isLikelyMedia(prepared.file)) { "待保存文件不是有效媒体" }

        val relativePath = policy.relativePath(media, resolution, fileNameRule)
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
    } }

    private fun customRelativePath(
        media: MediaItem,
        resolution: ResolutionPreset,
        fileNameRule: FileNameRule,
    ): String = policy.relativePath(media, resolution, fileNameRule).substringAfter('/')

    private fun publishToTree(
        treeUri: Uri,
        relativePath: String,
        prepared: PreparedMedia,
    ): StoredMedia {
        require(MediaFileValidator.isLikelyMedia(prepared.file)) { "待保存文件不是有效媒体" }
        val parts = relativePath.split('/').filter(String::isNotBlank)
        require(parts.size >= 2) { "自定义保存路径无效" }
        var parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        parts.dropLast(1).forEach { directory ->
            parent = findChild(parent, directory)
                ?: requireNotNull(
                    DocumentsContract.createDocument(
                        resolver,
                        parent,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        directory,
                    ),
                ) { "无法创建保存目录：$directory" }
        }
        val displayName = parts.last()
        val existing = findChild(parent, displayName)
        val destination = existing ?: requireNotNull(
            DocumentsContract.createDocument(resolver, parent, prepared.mimeType, displayName),
        ) { "无法在所选文件夹创建输出文件" }
        try {
            requireNotNull(resolver.openOutputStream(destination, "wt")) {
                "无法写入所选文件夹"
            }.use { output -> prepared.file.inputStream().use { input -> input.copyTo(output) } }
            val stored = StoredMedia(destination.toString(), prepared.file.length())
            require(isValid(stored)) { "写入自定义文件夹后校验失败" }
            prepared.file.delete()
            return stored
        } catch (error: Throwable) {
            if (existing == null) runCatching { DocumentsContract.deleteDocument(resolver, destination) }
            throw error
        }
    }

    private fun findTreeDocument(treeUri: Uri, relativePath: String): Uri? {
        var current = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        relativePath.split('/').filter(String::isNotBlank).forEach { name ->
            current = findChild(current, name) ?: return null
        }
        return current
    }

    private fun findChild(parent: Uri, displayName: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        return resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == displayName) {
                    return@use DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idColumn))
                }
            }
            null
        }
    }

    private fun loadIndex(): List<IndexedMedia> = cachedIndex ?: buildList {
        addAll(
            queryCollection(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "Movies/南烛枫视频下载器/%",
                MIN_CONTAINER_BYTES,
            ),
        )
        addAll(
            queryCollection(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "Music/南烛枫视频下载器/%",
                MIN_MP3_BYTES,
            ),
        )
    }.also { cachedIndex = it }

    private fun queryCollection(
        collection: Uri,
        relativePathPattern: String,
        minimumBytes: Long,
    ): List<IndexedMedia> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(relativePathPattern)
        return resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            buildList {
                while (cursor.moveToNext()) {
                    val size = cursor.getLong(sizeIndex)
                    if (size < minimumBytes) continue
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
        if (stored.fileSize < MIN_MP3_BYTES) return@runCatching false
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
        const val MIN_MP3_BYTES = 1024L
        const val MIN_CONTAINER_BYTES = 64 * 1024L
    }
}
