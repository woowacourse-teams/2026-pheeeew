package com.pheeeew.core.designsystem.input

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSMakeRange
import platform.Foundation.NSRange
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIFont
import platform.UIKit.UIKeyboardTypeASCIICapable
import platform.UIKit.UIKeyboardTypeDefault
import platform.UIKit.UILabel
import platform.UIKit.UIReturnKeyType.UIReturnKeyDefault
import platform.UIKit.UIReturnKeyType.UIReturnKeyDone
import platform.UIKit.UIReturnKeyType.UIReturnKeySearch
import platform.UIKit.UITextAutocapitalizationType.UITextAutocapitalizationTypeAllCharacters
import platform.UIKit.UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
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
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
internal actual fun dismissNativeTextKeyboard() {
    UIApplication.sharedApplication.sendAction(
        NSSelectorFromString("resignFirstResponder"),
        to = null,
        from = null,
        forEvent = null,
    )
}

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
    keyboardOptions: KeyboardOptions?,
    containerColor: Color?,
    acceptText: (String) -> Boolean,
) {
    val currentOnChange by rememberUpdatedState(onTextChange)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentNormalize by rememberUpdatedState(normalizeText)
    val currentAcceptText by rememberUpdatedState(acceptText)
    val editor =
        remember(singleLine) {
            NativeEditorDelegate(
                text,
                singleLine,
                { currentNormalize(it) },
                { currentOnChange(it) },
                { currentOnSearch() },
                { currentAcceptText(it) },
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
                    if (containerColor != null) {
                        UIColor(
                            red = containerColor.red.toDouble(),
                            green = containerColor.green.toDouble(),
                            blue = containerColor.blue.toDouble(),
                            alpha = containerColor.alpha.toDouble(),
                        )
                    } else if (singleLine) {
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
                if (keyboardOptions != null) {
                    keyboardType =
                        if (keyboardOptions.keyboardType ==
                            KeyboardType.Ascii
                        ) {
                            UIKeyboardTypeASCIICapable
                        } else {
                            UIKeyboardTypeDefault
                        }
                    returnKeyType =
                        when (keyboardOptions.imeAction) {
                            ImeAction.Done -> UIReturnKeyDone
                            ImeAction.Search -> UIReturnKeySearch
                            else -> UIReturnKeyDefault
                        }
                    autocapitalizationType =
                        when (keyboardOptions.capitalization) {
                            KeyboardCapitalization.Characters -> UITextAutocapitalizationTypeAllCharacters
                            KeyboardCapitalization.Sentences -> UITextAutocapitalizationTypeSentences
                            else -> UITextAutocapitalizationTypeNone
                        }
                    if (keyboardOptions.autoCorrectEnabled == false) {
                        autocorrectionType = UITextAutocorrectionTypeNo
                        spellCheckingType = UITextSpellCheckingTypeNo
                    }
                }
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
internal class NativeEditorDelegate(
    initialText: String,
    private val singleLine: Boolean,
    private val normalizeText: (TextFieldValue) -> TextFieldValue,
    private val onChange: (String) -> Unit,
    private val onSearch: () -> Unit,
    private val acceptText: (String) -> Boolean,
) : NSObject(),
    UITextViewDelegateProtocol {
    private val synchronization = TextEditorSynchronization(initialText)
    private var acceptedValue = TextFieldValue(initialText, TextRange(initialText.length))
    private var editRevision = 0L

    fun synchronize(
        view: UITextView,
        text: String,
    ) {
        // Never reflect a screen-state echo into UIKit's active marked-text buffer.
        if (view.markedTextRange != null || !synchronization.acceptExternal(text)) return
        editRevision++
        if (view.text != text) {
            val previous = view.selectedRange.useContents { location.toInt() }
            view.text = text
            view.selectedRange = NSMakeRange(previous.coerceAtMost(text.length).toULong(), 0u)
        }
        acceptedValue = valueOf(view)
    }

    override fun textViewDidChange(textView: UITextView) {
        val revision = ++editRevision
        val value = valueOf(textView)
        val raw = value.text
        val normalized = normalizeText(value)
        if (textView.markedTextRange != null) {
            if (acceptText(normalized.text)) {
                acceptedValue = normalized
                publish(normalized.text)
            }
            (textView as? EditingTextView)?.updatePlaceholder()
            return
        }
        val accepted = acceptText(normalized.text)
        val settled = normalized.takeIf { accepted } ?: acceptedValue
        acceptedValue = settled
        if (settled.text != raw) {
            textView.text = settled.text
            textView.selectedRange =
                NSMakeRange(settled.selection.min.toULong(), settled.selection.length.toULong())
        }
        if (!accepted) {
            // UIKit이 콜백 뒤에 적용하는 커서 이동도 복구하되, 이후의 새 편집은 덮어쓰지 않는다.
            dispatch_async(dispatch_get_main_queue()) {
                if (editRevision == revision && textView.delegate === this && textView.text == settled.text) {
                    textView.selectedRange =
                        NSMakeRange(settled.selection.min.toULong(), settled.selection.length.toULong())
                }
            }
        }
        (textView as? EditingTextView)?.updatePlaceholder()
        publish(settled.text)
    }

    override fun textViewDidEndEditing(textView: UITextView) {
        textViewDidChange(textView)
    }

    override fun textView(
        textView: UITextView,
        shouldChangeTextInRange: CValue<NSRange>,
        replacementText: String,
    ): Boolean {
        editRevision++
        if (singleLine && replacementText == "\n") {
            textView.resignFirstResponder()
            textViewDidChange(textView)
            onSearch()
            return false
        }
        // 한글 조합 콜백의 예상 삽입 길이는 실제 편집 결과와 다를 수 있어 결과에서 검증한다.
        val before = normalizeText(valueOf(textView))
        if (acceptText(before.text)) acceptedValue = before
        return true
    }

    private fun valueOf(view: UITextView): TextFieldValue {
        val selection = view.selectedRange.useContents { TextRange(location.toInt(), (location + length).toInt()) }
        return TextFieldValue(view.text.orEmpty(), selection)
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
