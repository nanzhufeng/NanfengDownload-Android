package com.nanzhufeng.videodownloader.feature.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** One input draft: explicit paste replaces it; ordinary edits retain selection/composition. */
internal object LinkInputEditing {
    fun paste(current: TextFieldValue, text: String?): TextFieldValue =
        if (text.isNullOrEmpty()) current else TextFieldValue(text, TextRange(text.length))

    // IME clipboard chips use commitText rather than the system Paste action. Inspect only
    // the inserted range, never poll the clipboard or mistake a URL already in the draft for one.
    fun edit(current: TextFieldValue, next: TextFieldValue): TextFieldValue {
        if (current.text.isEmpty() || current.composition != null || next.composition != null) return next
        val start = current.selection.min
        val end = current.selection.max
        val insertedEnd = next.text.length - (current.text.length - end)
        if (insertedEnd <= start || !next.selection.collapsed || next.selection.end != insertedEnd) return next
        if (!next.text.startsWith(current.text.substring(0, start)) ||
            !next.text.endsWith(current.text.substring(end))) return next
        val inserted = next.text.substring(start, insertedEnd)
        return if (Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).containsMatchIn(inserted)) {
            paste(current, inserted)
        } else next
    }
}
