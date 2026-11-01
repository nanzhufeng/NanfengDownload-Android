package com.nanzhufeng.videodownloader.domain.history

import kotlinx.coroutines.CancellationException

internal data class LocalDocumentReference(val treeId: String, val documentId: String)

/** A cleanup-local view of only the document IDs referenced by its history snapshot. */
internal class LocalDocumentScan(
    documents: Collection<LocalDocumentReference>,
    private val queryChildren: (treeId: String, parentId: String, wanted: Set<String>) -> Set<String>?,
) {
    private val wanted = mutableMapOf<Pair<String, String>, MutableSet<String>>()
    private val children = mutableMapOf<Pair<String, String>, Set<String>?>()
    private val absent = mutableMapOf<Pair<String, String>, MutableSet<String>>()

    init {
        for (document in documents) {
            val chain = localDocumentAncestry(document.treeId, document.documentId) ?: continue
            for ((parent, child) in chain.zipWithNext()) {
                wanted.getOrPut(document.treeId to parent) { linkedSetOf() }.add(child)
            }
        }
    }

    fun exists(document: LocalDocumentReference, cachedOnly: Boolean = false): Boolean? {
        val chain = localDocumentAncestry(document.treeId, document.documentId) ?: return null
        if (chain.size < 2) return null
        for ((parent, child) in chain.zipWithNext()) {
            val key = document.treeId to parent
            val ids = wanted[key] ?: return null
            if (key !in children) {
                if (cachedOnly) return null
                children[key] = read(key, ids)
            }
            val present = children[key] ?: return null
            if (child !in present) {
                absent.getOrPut(key) { linkedSetOf() }.add(child)
                return false
            }
        }
        return true
    }

    /** Refresh each negative parent once, including a parent shared by many missing files. */
    fun validate(): Boolean = absent.all { (key, missing) ->
        val present = read(key, missing) ?: return@all false
        missing.none { it in present }
    }

    private fun read(key: Pair<String, String>, ids: Set<String>): Set<String>? = try {
        queryChildren(key.first, key.second, ids)?.let { found -> ids.filter { it in found }.toSet() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
