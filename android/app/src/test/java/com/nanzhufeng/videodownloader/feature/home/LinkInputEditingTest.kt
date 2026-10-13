package com.nanzhufeng.videodownloader.feature.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkInputEditingTest {
    private val old = "旧分享 https://example.test/old 后缀"
    private val share = "复制打开抖音 https://v.douyin.com/new/ 10/13"

    @Test fun explicitPasteReplacesRegardlessOfCursorOrSelection() {
        for (selection in listOf(TextRange(0), TextRange(5), TextRange(old.length), TextRange(2, 8))) {
            val result = LinkInputEditing.paste(TextFieldValue(old, selection), share)
            assertEquals(share, result.text)
            assertEquals(TextRange(share.length), result.selection)
        }
    }

    @Test fun pastePreservesWholeBatchAndDoesNotTruncateLongText() {
        val batch = "https://example.test/a\nhttps://example.test/b\n" + "标题".repeat(600)
        assertEquals(batch, LinkInputEditing.paste(TextFieldValue(old), batch).text)
    }

    @Test fun explicitNonUrlPasteAlsoReplacesLikeDesktop() {
        assertEquals("分享文案", LinkInputEditing.paste(TextFieldValue(old), "分享文案").text)
    }

    @Test fun emptyClipboardDoesNotEraseDraftOrSelection() {
        val current = TextFieldValue(old, TextRange(3, 8))
        assertEquals(current, LinkInputEditing.paste(current, null))
        assertEquals(current, LinkInputEditing.paste(current, ""))
    }

    @Test fun imeLinkInsertionReplacesAtStartMiddleEndAndPartialSelection() {
        for (selection in listOf(TextRange(0), TextRange(5), TextRange(old.length), TextRange(2, 8))) {
            val current = TextFieldValue(old, selection)
            val text = old.replaceRange(selection.min, selection.max, share)
            val next = TextFieldValue(text, TextRange(selection.min + share.length))
            assertEquals(TextFieldValue(share, TextRange(share.length)), LinkInputEditing.edit(current, next))
        }
    }

    @Test fun pastingSameLinkAgainDoesNotDuplicateIt() {
        val url = "https://example.test/a"
        assertEquals(TextFieldValue(url, TextRange(url.length)), LinkInputEditing.edit(
            TextFieldValue(url, TextRange(url.length)), TextFieldValue(url + url, TextRange(url.length * 2)),
        ))
    }

    @Test fun typingEditingAndDeletingExistingUrlRemainNormal() {
        val current = TextFieldValue(old, TextRange(old.length))
        for (next in listOf(
            TextFieldValue(old + "a", TextRange(old.length + 1)),
            TextFieldValue(old.dropLast(1), TextRange(old.length - 1)),
            TextFieldValue(old, TextRange(3, 10)),
            TextFieldValue("", TextRange.Zero),
        )) assertEquals(next, LinkInputEditing.edit(current, next))
    }

    @Test fun chineseImeAndCompositionAreNotMistakenForPaste() {
        val current = TextFieldValue(old, TextRange(0))
        val chinese = TextFieldValue("新的标题$old", TextRange(4), TextRange(0, 4))
        assertEquals(chinese, LinkInputEditing.edit(current, chinese))
        val composedLink = TextFieldValue(share + old, TextRange(share.length), TextRange(0, share.length))
        assertEquals(composedLink, LinkInputEditing.edit(current, composedLink))
    }

    @Test fun emptyInputAndFullSelectionAcceptNewTextUnchanged() {
        val next = TextFieldValue(share, TextRange(share.length))
        assertEquals(next, LinkInputEditing.edit(TextFieldValue(""), next))
        assertEquals(next, LinkInputEditing.edit(TextFieldValue(old, TextRange(0, old.length)), next))
    }
}
