package com.nanzhufeng.videodownloader.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.nanzhufeng.videodownloader.domain.history.LocalMediaAvailability
import com.nanzhufeng.videodownloader.domain.history.LocalMediaAvailability.*
import com.nanzhufeng.videodownloader.domain.history.LocalMediaInspector
import com.nanzhufeng.videodownloader.domain.history.LocalDocumentReference
import com.nanzhufeng.videodownloader.domain.history.LocalDocumentScan
import com.nanzhufeng.videodownloader.domain.history.confirmLocalDocumentAbsence
import com.nanzhufeng.videodownloader.domain.history.localDocumentAncestry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Absence requires positive local-provider evidence. Access errors and offline providers remain UNKNOWN. */
internal class AndroidLocalMediaInspector(
    context: Context,
    private val cleanupUris: Collection<String>? = null,
    private val scanJob: Job? = null,
) : LocalMediaInspector {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private var opens = 0
    private var documentQueries = 0
    private var pathQueries = 0
    private var childQueries = 0
    private var volumeQueries = 0
    private var grantQueries = 0
    private var volumeSnapshot: List<StorageVolume>? = null
    private var grantSnapshot: Set<String>? = null
    private val usedMountedVolumes = mutableSetOf<String>()
    private val usedGrants = mutableSetOf<String>()
    private val missingTrees = mutableSetOf<String>()
    private val references = cleanupUris.orEmpty().mapNotNull { source ->
        localReference(Uri.parse(source))?.let { source to it }
    }.toMap()
    private val treeUris = references.entries.associate { it.value.treeId to Uri.parse(it.key) }
    private val directories = cleanupUris?.let {
        LocalDocumentScan(references.values) { tree, parent, wanted ->
            readChildren(treeUris.getValue(tree), parent, wanted)
        }
    }

    override suspend fun forCleanup(uris: Collection<String>): LocalMediaInspector {
        val job = currentCoroutineContext()[Job]
        return withContext(Dispatchers.IO) { AndroidLocalMediaInspector(appContext, uris, job) }
    }

    override suspend fun isSnapshotStillValid(): Boolean = withContext(Dispatchers.IO) {
        val valid = try {
            val volumesValid = usedMountedVolumes.isEmpty() || readVolumes().let { fresh ->
                usedMountedVolumes.all { mountedIn(fresh, it) }
            }
            val grantsValid = usedGrants.isEmpty() || readGrants().containsAll(usedGrants)
            volumesValid && grantsValid && directories?.validate() != false && missingTrees.all { tree ->
                inspectDocument(DocumentsContract.buildDocumentUriUsingTree(treeUris.getValue(tree), tree)) == MISSING
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { false }
        if (cleanupUris != null) android.util.Log.i("HistoryCleanupIO",
            "opens=$opens documents=$documentQueries paths=$pathQueries children=$childQueries volumes=$volumeQueries grants=$grantQueries valid=$valid")
        valid
    }

    override suspend fun inspect(uri: String): LocalMediaAvailability = withContext(Dispatchers.IO) {
        try {
            val target = Uri.parse(uri)
            val reference = references[uri]
            // Reuse only proven absence within this operation, never an old UI/fileExists flag.
            if (reference != null &&
                (reference.treeId in missingTrees || directories?.exists(reference, cachedOnly = true) == false) &&
                canConfirmTree(reference.treeId)) return@withContext MISSING
            when (target.scheme) {
                "file" -> inspectFile(File(target.path ?: return@withContext UNKNOWN))
                "content" -> try {
                    opens++
                    resolver.openFileDescriptor(target, "r")?.use { AVAILABLE } ?: UNKNOWN
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    confirmLocalAbsence(target)
                }
                else -> UNKNOWN
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { UNKNOWN }
    }

    private fun inspectFile(file: File): LocalMediaAvailability = when {
        file.isFile && file.canRead() -> AVAILABLE
        file.exists() -> UNKNOWN
        file.parentFile?.let { it.isDirectory && it.canRead() } == true -> MISSING
        else -> UNKNOWN
    }

    private fun readVolumes(): List<StorageVolume> {
        volumeQueries++
        return appContext.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty()
    }

    private fun mountedIn(volumes: List<StorageVolume>, volumeId: String): Boolean {
        val volume = volumes.firstOrNull {
            if (volumeId in setOf("external", "external_primary", "primary")) it.isPrimary
            else it.uuid?.equals(volumeId, ignoreCase = true) == true
        } ?: return false
        return volume.state == Environment.MEDIA_MOUNTED || volume.state == Environment.MEDIA_MOUNTED_READ_ONLY
    }

    private fun isMounted(volumeId: String): Boolean {
        val volumes = if (cleanupUris == null) readVolumes()
            else volumeSnapshot ?: readVolumes().also { volumeSnapshot = it }
        return mountedIn(volumes, volumeId).also { if (it && cleanupUris != null) usedMountedVolumes += volumeId }
    }

    private fun readGrants(): Set<String> {
        grantQueries++
        return resolver.persistedUriPermissions.mapNotNull {
            if (!it.isReadPermission || it.uri.authority != "com.android.externalstorage.documents" ||
                !DocumentsContract.isTreeUri(it.uri)) null
            else DocumentsContract.getTreeDocumentId(it.uri)
        }.toSet()
    }

    private fun canConfirmTree(tree: String): Boolean {
        if (!isMounted(tree.substringBefore(':'))) return false
        val grants = if (cleanupUris == null) readGrants()
            else grantSnapshot ?: readGrants().also { grantSnapshot = it }
        return (tree in grants).also { if (it && cleanupUris != null) usedGrants += tree }
    }

    private fun localReference(uri: Uri): LocalDocumentReference? = try {
        if (uri.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(uri)) null
        else LocalDocumentReference(DocumentsContract.getTreeDocumentId(uri), DocumentsContract.getDocumentId(uri))
    } catch (_: Exception) { null }

    private fun confirmLocalAbsence(uri: Uri): LocalMediaAvailability {
        if (uri.authority == "media") {
            if (!isMounted(uri.pathSegments.firstOrNull().orEmpty())) return UNKNOWN
            documentQueries++
            return resolver.query(uri, arrayOf("_id"), null, null, null)?.use {
                if (it.moveToFirst()) UNKNOWN else MISSING
            } ?: UNKNOWN
        }
        val reference = localReference(uri) ?: return UNKNOWN
        if (!canConfirmTree(reference.treeId)) return UNKNOWN
        // A complete shared parent listing avoids leaf open/query/path failures for every sibling.
        if (directories?.exists(reference) == false) return MISSING
        val document = inspectDocument(uri)
        if (document == MISSING) return MISSING
        val root = inspectDocument(DocumentsContract.buildDocumentUriUsingTree(uri, reference.treeId))
        if (root == MISSING && cleanupUris != null) missingTrees += reference.treeId
        return root
    }

    private fun inspectDocument(uri: Uri): LocalMediaAvailability = confirmLocalDocumentAbsence(
        mountedAndGranted = true,
        queryExists = {
            documentQueries++
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() }
        },
        findDocumentPath = {
            pathQueries++
            DocumentsContract.findDocumentPath(resolver, uri)
            Unit
        },
        queryAncestorExists = { inspectAuthorizedAncestry(uri) },
    )

    private fun inspectAuthorizedAncestry(uri: Uri): Boolean? {
        val reference = localReference(uri) ?: return null
        directories?.let { return it.exists(reference) }
        val chain = localDocumentAncestry(reference.treeId, reference.documentId) ?: return null
        if (chain.size < 2) return null
        for ((parent, child) in chain.zipWithNext()) {
            val present = readChildren(uri, parent, setOf(child)) ?: return null
            if (child !in present) return false
        }
        return true
    }

    /** Retain only requested IDs; a large unrelated directory must not inflate this scan's cache. */
    private fun readChildren(uri: Uri, parent: String, wanted: Set<String>): Set<String>? {
        scanJob?.ensureActive()
        childQueries++
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parent)
        return resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            if (column < 0) return@use null
            val found = hashSetOf<String>()
            while (cursor.moveToNext()) {
                scanJob?.ensureActive()
                cursor.getString(column)?.takeIf { it in wanted }?.let(found::add)
                if (found.size == wanted.size) break
            }
            when {
                found.size == wanted.size -> found
                cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) -> null
                !cursor.extras.getString(DocumentsContract.EXTRA_ERROR).isNullOrBlank() -> null
                else -> found
            }
        }
    }
}
