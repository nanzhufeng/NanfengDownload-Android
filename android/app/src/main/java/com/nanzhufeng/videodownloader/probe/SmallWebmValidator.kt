package com.nanzhufeng.videodownloader.probe

/** Bounded EBML preflight; signatures alone must not admit tiny error/truncated bodies. */
internal object SmallWebmValidator {
    fun accepts(bytes: ByteArray): Boolean {
        val root = elements(bytes, 0, bytes.size) ?: return false
        val header = root.firstOrNull()?.takeIf { it.id == 0x1a45dfa3L } ?: return false
        val docType = elements(bytes, header.payload, header.end)?.singleOrNull { it.id == 0x4282L } ?: return false
        if (String(bytes, docType.payload, docType.end - docType.payload, Charsets.US_ASCII) != "webm") return false
        val segment = root.singleOrNull { it.id == 0x18538067L } ?: return false
        val content = elements(bytes, segment.payload, segment.end) ?: return false
        val tracks = content.singleOrNull { it.id == 0x1654ae6bL } ?: return false
        val entries = elements(bytes, tracks.payload, tracks.end)?.filter { it.id == 0xaeL } ?: return false
        if (entries.isEmpty() || entries.any { track ->
                val fields = elements(bytes, track.payload, track.end) ?: return false
                fields.none { it.id == 0xd7L && unsigned(bytes, it) > 0L } ||
                    fields.none { it.id == 0x83L && unsigned(bytes, it) in 1L..2L } ||
                    fields.none { it.id == 0x86L && it.end > it.payload }
            }) return false
        val clusters = content.filter { it.id == 0x1f43b675L }
        if (clusters.isEmpty()) return false
        return clusters.all { cluster ->
            val fields = elements(bytes, cluster.payload, cluster.end) ?: return false
            if (fields.none { it.id == 0xe7L && it.end > it.payload }) return false
            val blocks = fields.flatMap { field ->
                if (field.id == 0xa0L) elements(bytes, field.payload, field.end) ?: return false
                else listOf(field)
            }.filter { it.id == 0xa3L || it.id == 0xa1L }
            blocks.isNotEmpty() && blocks.all {
                val track = vint(bytes, it.payload, it.end, retainMarker = false) ?: return false
                track.value > 0 && it.end - it.payload > track.length + 3
            }
        }
    }

    private data class Element(val id: Long, val payload: Int, val end: Int)
    private data class Vint(val value: Long, val length: Int, val unknown: Boolean)

    private fun elements(bytes: ByteArray, start: Int, end: Int): List<Element>? {
        val result = mutableListOf<Element>()
        var position = start
        while (position < end) {
            val id = vint(bytes, position, end, retainMarker = true) ?: return null
            if (id.length > 4) return null
            val size = vint(bytes, position + id.length, end, retainMarker = false) ?: return null
            val payload = position + id.length + size.length
            val length = if (size.unknown) {
                if (id.value != 0x18538067L && id.value != 0x1f43b675L) return null
                (end - payload).toLong()
            } else size.value
            if (length > end - payload) return null
            val elementEnd = payload + length.toInt()
            result += Element(id.value, payload, elementEnd)
            position = elementEnd
        }
        return result
    }

    private fun vint(bytes: ByteArray, at: Int, end: Int, retainMarker: Boolean): Vint? {
        if (at >= end) return null
        val first = bytes[at].toInt() and 255
        var marker = 128
        var length = 1
        while (length <= 8 && first and marker == 0) { marker = marker ushr 1; length++ }
        if (length > 8 || end - at < length) return null
        var value = (if (retainMarker) first else first and (marker - 1)).toLong()
        for (index in 1 until length) value = (value shl 8) or (bytes[at + index].toLong() and 255L)
        return Vint(value, length, !retainMarker && value == (1L shl (7 * length)) - 1)
    }

    private fun unsigned(bytes: ByteArray, element: Element): Long {
        if (element.end - element.payload !in 1..8) return -1L
        return (element.payload until element.end).fold(0L) { value, at -> (value shl 8) or (bytes[at].toLong() and 255L) }
    }
}
