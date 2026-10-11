package com.nanzhufeng.videodownloader.probe

/** Bounded structural check for complete, ordinary MP4/M4A files below 64 KiB.
 * This is a container preflight, not proof that every compressed sample decodes.
 */
internal object SmallIsoBmffValidator {
    fun accepts(bytes: ByteArray): Boolean {
        val top = boxes(bytes, 0, bytes.size) ?: return false
        if (top.firstOrNull()?.let { it.type == "ftyp" && it.end - it.payload >= 8 } != true) return false
        val media = top.filter { it.type == "mdat" && it.end > it.payload }
        if (media.isEmpty()) return false
        val movie = top.singleOrNull { it.type == "moov" } ?: return false
        val children = boxes(bytes, movie.payload, movie.end) ?: return false
        if (children.none { it.type == "mvhd" }) return false
        val tracks = children.filter { it.type == "trak" }
        if (tracks.isEmpty()) return false
        return tracks.all { track ->
            val trackChildren = boxes(bytes, track.payload, track.end) ?: return false
            if (trackChildren.none { it.type == "tkhd" }) return false
            val mdia = trackChildren.singleOrNull { it.type == "mdia" } ?: return false
            val mdiaChildren = boxes(bytes, mdia.payload, mdia.end) ?: return false
            if (mdiaChildren.none { it.type == "mdhd" } || mdiaChildren.none { it.type == "hdlr" }) return false
            val minf = mdiaChildren.singleOrNull { it.type == "minf" } ?: return false
            val stbl = boxes(bytes, minf.payload, minf.end)?.singleOrNull { it.type == "stbl" } ?: return false
            val tables = boxes(bytes, stbl.payload, stbl.end) ?: return false
            val description = tables.singleOrNull { it.type == "stsd" } ?: return false
            if (description.end - description.payload < 8 || uint(bytes, description.payload + 4) == 0L) return false
            val entries = boxes(bytes, description.payload + 8, description.end) ?: return false
            if (entries.size.toLong() != uint(bytes, description.payload + 4)) return false
            val samples = tables.singleOrNull { it.type == "stsz" } ?: return false
            if (samples.end - samples.payload < 12) return false
            val sampleSize = uint(bytes, samples.payload + 4)
            val count = uint(bytes, samples.payload + 8)
            if (count == 0L || count > bytes.size) return false
            val sampleBytes = if (sampleSize > 0L) sampleSize * count else {
                if (count * 4L != samples.end.toLong() - samples.payload - 12) return false
                (0 until count.toInt()).sumOf { uint(bytes, samples.payload + 12 + it * 4) }
            }
            if (sampleBytes <= 0 || sampleBytes > media.sumOf { (it.end - it.payload).toLong() }) return false
            val offsets = tables.singleOrNull { it.type == "stco" || it.type == "co64" } ?: return false
            val entrySize = if (offsets.type == "co64") 8 else 4
            if (offsets.end - offsets.payload < 8) return false
            val chunks = uint(bytes, offsets.payload + 4)
            if (chunks <= 0L || chunks * entrySize != offsets.end.toLong() - offsets.payload - 8) return false
            (0 until chunks.toInt()).all { index ->
                val position = offsets.payload + 8 + index * entrySize
                if (entrySize == 8 && uint(bytes, position) != 0L) return false
                val offset = uint(bytes, position + entrySize - 4)
                media.any { offset >= it.payload && offset < it.end }
            }
        }
    }

    private data class Box(val type: String, val payload: Int, val end: Int)

    private fun boxes(bytes: ByteArray, start: Int, end: Int): List<Box>? {
        val result = mutableListOf<Box>()
        var position = start
        while (position < end) {
            if (end - position < 8) return null
            var size = uint(bytes, position)
            var header = 8
            if (size == 1L) {
                if (end - position < 16 || uint(bytes, position + 8) != 0L) return null
                size = uint(bytes, position + 12)
                header = 16
            } else if (size == 0L) size = (end - position).toLong()
            if (size < header || size > end - position) return null
            result += Box(String(bytes, position + 4, 4, Charsets.US_ASCII), position + header, position + size.toInt())
            position += size.toInt()
        }
        return result
    }

    private fun uint(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, index -> (value shl 8) or (bytes[at + index].toLong() and 255L) }
}
