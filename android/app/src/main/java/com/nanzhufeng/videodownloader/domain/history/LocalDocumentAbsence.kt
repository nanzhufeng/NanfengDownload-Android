package com.nanzhufeng.videodownloader.domain.history

import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException

/** Only the mounted, still-authorized local document provider may confirm absence. */
internal fun confirmLocalDocumentAbsence(
    mountedAndGranted: Boolean,
    queryExists: () -> Boolean?,
    findDocumentPath: () -> Unit,
    queryAncestorExists: () -> Boolean? = { null },
): LocalMediaAvailability {
    if (!mountedAndGranted) return LocalMediaAvailability.UNKNOWN
    val exists = try {
        queryExists()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        null
    } catch (_: FileNotFoundException) {
        return LocalMediaAvailability.MISSING
    } catch (_: Exception) {
        null
    }
    if (exists == false) return LocalMediaAvailability.MISSING
    return try {
        // DocumentsProvider may return null or reject a descendant when its tree is gone.
        // A typed missing result proves absence; otherwise an authorized ancestor listing may do so.
        findDocumentPath()
        LocalMediaAvailability.UNKNOWN
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: FileNotFoundException) {
        LocalMediaAvailability.MISSING
    } catch (_: Exception) {
        try {
            if (queryAncestorExists() == false) LocalMediaAvailability.MISSING
            else LocalMediaAvailability.UNKNOWN
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LocalMediaAvailability.UNKNOWN
        }
    }
}

/** Local ExternalStorageProvider IDs only; never traverse above the persisted tree. */
internal fun localDocumentAncestry(treeId: String, documentId: String): List<String>? {
    fun parts(id: String): Pair<String, List<String>>? {
        val split = id.split(':', limit = 2)
        if (split.size != 2 || split[0].isBlank() || '\u0000' in id) return null
        val path = split[1].takeIf(String::isNotEmpty)?.split('/').orEmpty()
        if (path.any { it.isEmpty() || it == "." || it == ".." }) return null
        return split[0] to path
    }
    val (volume, tree) = parts(treeId) ?: return null
    val (targetVolume, target) = parts(documentId) ?: return null
    if (volume != targetVolume || target.size < tree.size || target.take(tree.size) != tree) return null
    return (tree.size..target.size).map { count -> "$volume:${target.take(count).joinToString("/")}" }
}
