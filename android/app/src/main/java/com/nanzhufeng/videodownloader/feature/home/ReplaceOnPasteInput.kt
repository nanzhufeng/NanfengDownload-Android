package com.nanzhufeng.videodownloader.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Shared by compact and expanded home, without changing the existing field appearance. */
@Composable
internal fun ReplaceOnPasteInput(
    text: String,
    onTextChange: (String) -> Unit,
    content: @Composable (TextFieldValue, (TextFieldValue) -> Unit, Modifier) -> Unit,
) {
    var editor by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    // Parent owns the draft (including Clear/share/restore); local state owns cursor and IME only.
    val value = if (editor.text == text) editor else TextFieldValue(text, TextRange(text.length))
    // The platform menu may retain its callback while the draft changes externally.
    val currentValue by rememberUpdatedState(value)
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val clipboard = LocalClipboardManager.current
    val toolbar = LocalTextToolbar.current
    val update: (TextFieldValue) -> Unit = { next ->
        editor = next
        if (next.text != currentValue.text) currentOnTextChange(next.text)
    }
    val paste: () -> Unit = {
        update(LinkInputEditing.paste(currentValue, clipboard.getText()?.text))
        toolbar.hide()
    }
    val replacementToolbar = object : TextToolbar {
        override val status get() = toolbar.status
        override fun hide() = toolbar.hide()
        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) = toolbar.showMenu(rect, onCopyRequested, onPasteRequested?.let { paste },
            onCutRequested, onSelectAllRequested)
    }
    val keys = Modifier.onPreviewKeyEvent { event ->
        val isPaste = (event.isCtrlPressed && event.key == Key.V) ||
            (event.isShiftPressed && event.key == Key.Insert)
        if (isPaste) {
            if (event.type == KeyEventType.KeyDown) paste()
            true
        } else false
    }
    CompositionLocalProvider(LocalTextToolbar provides replacementToolbar) {
        content(value, { update(LinkInputEditing.edit(currentValue, it)) }, keys)
    }
}
