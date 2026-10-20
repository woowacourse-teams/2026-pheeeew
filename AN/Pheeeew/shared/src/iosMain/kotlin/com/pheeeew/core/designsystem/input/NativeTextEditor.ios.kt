package com.pheeeew.core.designsystem.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSMakeRange
import platform.Foundation.NSRange
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIFont
import platform.UIKit.UIKeyboardTypeASCIICapable
import platform.UIKit.UIKeyboardTypeDefault
import platform.UIKit.UILabel
import platform.UIKit.UIReturnKeyType.UIReturnKeyDefault
import platform.UIKit.UIReturnKeyType.UIReturnKeySearch
import platform.UIKit.UITextAutocapitalizationType.UITextAutocapitalizationTypeAllCharacters
import platform.UIKit.UITextAutocapitalizationType.UITextAutocapitalizationTypeSentences
import platform.UIKit.UITextAutocorrectionType.UITextAutocorrectionTypeDefault
import platform.UIKit.UITextAutocorrectionType.UITextAutocorrectionTypeNo
import platform.UIKit.UITextSmartInsertDeleteType.UITextSmartInsertDeleteTypeNo
import platform.UIKit.UITextSpellCheckingType.UITextSpellCheckingTypeDefault
import platform.UIKit.UITextSpellCheckingType.UITextSpellCheckingTypeNo
import platform.UIKit.UITextView
import platform.UIKit.UITextViewDelegateProtocol
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

internal actual val usesNativeTextEditor: Boolean = true

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun NativeTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier,
    label: String,
    normalizeText: (TextFieldValue) -> TextFieldValue,
    singleLine: Boolean,
    enabled: Boolean,
    onSearch: () -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onTextChange)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentNormalize by rememberUpdatedState(normalizeText)
    val editor =
        remember(singleLine) {
            NativeEditorDelegate(
                text,
                singleLine,
                { currentNormalize(it) },
                { currentOnChange(it) },
                { currentOnSearch() },
            )
        }
    UIKitView(
        modifier = modifier,
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
        factory = {
            EditingTextView(label).apply {
                this.text = text
                delegate = editor
                backgroundColor =
                    if (singleLine) {
                        UIColor(
                            red = 242.0 / 255,
                            green = 244.0 / 255,
                            blue = 243.0 / 255,
                            alpha = 1.0,
                        )
                    } else {
                        UIColor.whiteColor
                    }
                textColor = UIColor.blackColor
                font = UIFont.systemFontOfSize(if (singleLine) 16.0 else 14.0)
                textContainerInset = UIEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
                textContainer.lineFragmentPadding = 0.0
                keyboardType = if (singleLine) UIKeyboardTypeASCIICapable else UIKeyboardTypeDefault
                returnKeyType = if (singleLine) UIReturnKeySearch else UIReturnKeyDefault
                autocapitalizationType =
                    if (singleLine) {
                        UITextAutocapitalizationTypeAllCharacters
                    } else {
                        UITextAutocapitalizationTypeSentences
                    }
                autocorrectionType = if (singleLine) UITextAutocorrectionTypeNo else UITextAutocorrectionTypeDefault
                spellCheckingType = if (singleLine) UITextSpellCheckingTypeNo else UITextSpellCheckingTypeDefault
                smartInsertDeleteType = UITextSmartInsertDeleteTypeNo
                accessibilityLabel = label
            }
        },
        update = { view ->
            view.setEditable(enabled)
            view.selectable = enabled
            editor.synchronize(view, text)
        },
        onRelease = { view ->
            view.delegate = null
            view.resignFirstResponder()
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class NativeEditorDelegate(
    initialText: String,
    private val singleLine: Boolean,
    private val normalizeText: (TextFieldValue) -> TextFieldValue,
    private val onChange: (String) -> Unit,
    private val onSearch: () -> Unit,
) : NSObject(),
    UITextViewDelegateProtocol {
    private val synchronization = TextEditorSynchronization(initialText)

    fun synchronize(
        view: UITextView,
        text: String,
    ) {
        // Never reflect a screen-state echo into UIKit's active marked-text buffer.
        if (view.markedTextRange != null || !synchronization.acceptExternal(text)) return
        if (view.text != text) {
            val previous = view.selectedRange.useContents { location.toInt() }
            view.text = text
            view.selectedRange = NSMakeRange(previous.coerceAtMost(text.length).toULong(), 0u)
        }
    }

    override fun textViewDidChange(textView: UITextView) {
        (textView as? EditingTextView)?.updatePlaceholder()
        val raw = textView.text.orEmpty()
        val selection = textView.selectedRange.useContents { TextRange(location.toInt(), (location + length).toInt()) }
        if (textView.markedTextRange != null) {
            publish(normalizeText(TextFieldValue(raw, selection)).text)
            return
        }
        val normalized = normalizeText(TextFieldValue(raw, selection))
        if (normalized.text != raw) {
            textView.text = normalized.text
            textView.selectedRange =
                NSMakeRange(normalized.selection.min.toULong(), normalized.selection.length.toULong())
        }
        publish(normalized.text)
    }

    override fun textViewDidEndEditing(textView: UITextView) {
        textViewDidChange(textView)
    }

    override fun textView(
        textView: UITextView,
        shouldChangeTextInRange: CValue<NSRange>,
        replacementText: String,
    ): Boolean {
        if (singleLine && replacementText == "\n") {
            textView.resignFirstResponder()
            textViewDidChange(textView)
            onSearch()
            return false
        }
        return true
    }

    private fun publish(text: String) {
        if (synchronization.publish(text)) onChange(text)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class EditingTextView(
    label: String,
) : UITextView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), textContainer = null) {
    private val hint =
        UILabel().apply {
            text = label
            textColor = UIColor.grayColor
            font = UIFont.systemFontOfSize(16.0)
            userInteractionEnabled = false
        }

    init {
        addSubview(hint)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val width = bounds.useContents { size.width }
        hint.setFrame(CGRectMake(0.0, 0.0, width, 22.0))
        updatePlaceholder()
    }

    fun updatePlaceholder() {
        hint.setHidden(!text.isNullOrEmpty())
    }
}
