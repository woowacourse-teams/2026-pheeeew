package com.pheeeew.core.designsystem.input

import androidx.compose.ui.text.TextRange
import com.pheeeew.feature.screens.settings.components.acceptedNicknameInput
import com.pheeeew.feature.screens.settings.components.normalizeNicknameValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.Foundation.NSMakeRange
import platform.UIKit.UITextView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class NativeNicknameEditorTest {
    @Test
    fun delayedScreenEchoPreservesNewerNativeTextAndMiddleSelection() {
        val published = mutableListOf<String>()
        val editor = nicknameDelegate(onChange = published::add)
        val view = UITextView()
        listOf("왈", "왈라", "왈라랄").forEach {
            view.text = it
            view.selectedRange = NSMakeRange(it.length.toULong(), 0u)
            editor.textViewDidChange(view)
        }
        view.selectedRange = NSMakeRange(1u, 0u)

        editor.synchronize(view, "왈")
        editor.synchronize(view, "왈라랄")

        assertEquals(listOf("왈", "왈라", "왈라랄"), published)
        assertEquals("왈라랄", view.text)
        assertEquals(TextRange(1), view.selection())
    }

    @Test
    fun markedKoreanTextSurvivesScreenSynchronization() {
        val editor = nicknameDelegate()
        val view = UITextView()
        view.setMarkedText("왈", NSMakeRange(1u, 0u))
        assertNotNull(view.markedTextRange)
        editor.textViewDidChange(view)

        editor.synchronize(view, "")

        assertEquals("왈", view.text)
        assertNotNull(view.markedTextRange)
        assertEquals(TextRange(1), view.selection())
    }

    @Test
    fun fullNicknameRejectsAnOverLimitMiddleInsertBeforeChangingText() {
        val editor = nicknameDelegate()
        val original = "가나다라마바사아자차"
        val view =
            UITextView().apply {
                text = original
                selectedRange = NSMakeRange(3u, 0u)
            }

        assertFalse(editor.textView(view, NSMakeRange(3u, 0u), "입력"))

        assertEquals(original, view.text)
        assertEquals(TextRange(3), view.selection())
        assertTrue(editor.textView(view, NSMakeRange(3u, 2u), "수정"))
    }

    @Test
    fun fullNicknameRejectsAnExtraCharacterWithoutReplacingTheLastCharacter() {
        val published = mutableListOf<String>()
        val editor = nicknameDelegate(onChange = published::add)
        val original = "가나다라마바사아자차"
        val caret = original.length
        val view =
            UITextView().apply {
                text = original
                selectedRange = NSMakeRange(caret.toULong(), 0u)
            }

        assertFalse(editor.textView(view, NSMakeRange(caret.toULong(), 0u), "입"))

        assertEquals(original, view.text)
        assertEquals(TextRange(caret), view.selection())
        assertTrue(published.isEmpty())
    }

    @Test
    fun activeKoreanCompositionCanReplaceItsJamoAtTheLengthBoundary() {
        val published = mutableListOf<String>()
        val editor = nicknameDelegate(onChange = published::add)
        val view =
            UITextView().apply {
                text = "가나라마바사아자차"
                selectedRange = NSMakeRange(2u, 0u)
                setMarkedText("ㅅ", NSMakeRange(1u, 0u))
            }

        assertNotNull(view.markedTextRange)
        assertTrue(editor.textView(view, NSMakeRange(3u, 0u), "ㅜ"))
        view.setMarkedText("수", NSMakeRange(1u, 0u))
        editor.textViewDidChange(view)

        assertEquals("가나수라마바사아자차", view.text)
        assertEquals(TextRange(3), view.selection())
        assertEquals(listOf("가나수라마바사아자차"), published)
    }

    @Test
    fun overLimitPasteKeepsTheSelectedTextAndExistingSuffixUntouched() {
        val editor = nicknameDelegate()
        val view =
            UITextView().apply {
                text = "1234567890"
                selectedRange = NSMakeRange(2u, 3u)
            }

        assertFalse(editor.textView(view, NSMakeRange(2u, 3u), "긴붙여넣기"))

        assertEquals("1234567890", view.text)
        assertEquals(TextRange(2, 5), view.selection())
    }

    @Test
    fun rejectedPasteRestoresCaretAfterUIKitAppliesItsLateSelection() {
        val editor = nicknameDelegate()
        val view =
            UITextView().apply {
                delegate = editor
                text = "1234567890"
                selectedRange = NSMakeRange(3u, 0u)
            }
        editor.textView(view, NSMakeRange(3u, 0u), "123")
        view.text = "1231234567890"
        view.selectedRange = NSMakeRange(6u, 0u)
        editor.textViewDidChange(view)
        view.selectedRange = NSMakeRange(6u, 0u)

        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.02, false)

        assertEquals("1234567890", view.text)
        assertEquals(TextRange(3), view.selection())
    }

    @Test
    fun queuedCaretRestoreDoesNotOverwriteANewerEdit() {
        val editor = nicknameDelegate()
        val view =
            UITextView().apply {
                delegate = editor
                text = "1234567890"
                selectedRange = NSMakeRange(3u, 0u)
            }
        editor.textView(view, NSMakeRange(3u, 0u), "123")
        view.text = "1231234567890"
        view.selectedRange = NSMakeRange(6u, 0u)
        editor.textViewDidChange(view)
        editor.textView(view, NSMakeRange(2u, 1u), "")
        view.text = "124567890"
        view.selectedRange = NSMakeRange(2u, 0u)
        editor.textViewDidChange(view)

        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.02, false)

        assertEquals("124567890", view.text)
        assertEquals(TextRange(2), view.selection())
    }

    @Test
    fun donePublishesLatestTextWithoutInsertingNewline() {
        val published = mutableListOf<String>()
        var doneText: String? = null
        val editor = nicknameDelegate(onChange = published::add, onDone = { doneText = published.last() })
        val view =
            UITextView().apply {
                text = "고양이"
                selectedRange = NSMakeRange(3u, 0u)
            }

        assertFalse(editor.textView(view, NSMakeRange(3u, 0u), "\n"))

        assertEquals("고양이", doneText)
        assertEquals("고양이", view.text)
    }

    private fun nicknameDelegate(
        onChange: (String) -> Unit = {},
        onDone: () -> Unit = {},
    ): NativeEditorDelegate =
        NativeEditorDelegate(
            initialText = "",
            singleLine = true,
            normalizeText = { it.normalizeNicknameValue() },
            onChange = onChange,
            onSearch = onDone,
            acceptText = { acceptedNicknameInput(it) != null },
        )

    private fun UITextView.selection(): TextRange =
        selectedRange.useContents { TextRange(location.toInt(), (location + length).toInt()) }
}
