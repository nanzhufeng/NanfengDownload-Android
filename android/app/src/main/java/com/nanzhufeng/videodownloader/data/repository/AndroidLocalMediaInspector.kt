package com.nanzhufeng.videodownloader.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.nanzhufeng.videodownloader.domain.history.LocalMediaAvailability
import com.nanzhufeng.videodownloader.domain.history.LocalMediaAvailability.*
import com.nanzhufeng.videodownloader.domain.history.LocalMediaInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** Absence requires positive local-provider evidence. Access errors and offline providers remain UNKNOWN. */
internal class AndroidLocalMediaInspector(context: Context) : LocalMediaInspector {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun inspect(uri: String): LocalMediaAvailability = withContext(Dispatchers.IO) {
        try {
            val target = Uri.parse(uri)
            when (target.scheme) {
                "file" -> inspectFile(File(target.path ?: return@withContext UNKNOWN))
                "content" -> try {
                    resolver.openFileDescriptor(target, "r")?.use { AVAILABLE } ?: UNKNOWN
                } catch (_: FileNotFoundException) {
                    confirmLocalAbsence(target)
                }
                else -> UNKNOWN
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            UNKNOWN
        } catch (_: Exception) {
            UNKNOWN
        }
    }

    private fun inspectFile(file: File): LocalMediaAvailability = when {
        file.isFile && file.canRead() -> AVAILABLE
        file.exists() -> UNKNOWN
        file.parentFile?.let { it.isDirectory && it.canRead() } == true -> MISSING
        else -> UNKNOWN
    }

    private fun isMounted(volumeId: String): Boolean {
        val volumes = appContext.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty()
        val volume = volumes.firstOrNull {
            if (volumeId in setOf("external", "external_primary", "primary")) it.isPrimary
            else it.uuid?.equals(volumeId, ignoreCase = true) == true
        } ?: return false
        return volume.state == Environment.MEDIA_MOUNTED || volume.state == Environment.MEDIA_MOUNTED_READ_ONLY
    }

    private fun confirmLocalAbsence(uri: Uri): LocalMediaAvailability {
        if (uri.authority == "media") {
            if (!isMounted(uri.pathSegments.firstOrNull().orEmpty())) return UNKNOWN
            return resolver.query(uri, arrayOf("_id"), null, null, null)?.use {
                if (it.moveToFirst()) UNKNOWN else MISSING
            } ?: UNKNOWN
        }
        // Only a local SAF provider with a readable granted tree can confirm a document is gone.
        // A disconnected volume, revoked grant, cloud provider or unreadable tree is not absence.
        if (uri.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(uri)) {
            return UNKNOWN
        }
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        if (!isMounted(treeId.substringBefore(':'))) return UNKNOWN
        val granted = resolver.persistedUriPermissions.any {
            it.isReadPermission && it.uri.authority == uri.authority &&
                DocumentsContract.isTreeUri(it.uri) && DocumentsContract.getTreeDocumentId(it.uri) == treeId
        }
        if (!granted) return UNKNOWN
        val treeRoot = DocumentsContract.buildDocumentUriUsingTree(uri, treeId)
        val rootReadable = resolver.query(treeRoot, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { it.moveToFirst() } == true
        if (!rootReadable) return UNKNOWN
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) UNKNOWN else MISSING
            } ?: UNKNOWN
        } catch (_: FileNotFoundException) {
            MISSING
        }
    }
}
