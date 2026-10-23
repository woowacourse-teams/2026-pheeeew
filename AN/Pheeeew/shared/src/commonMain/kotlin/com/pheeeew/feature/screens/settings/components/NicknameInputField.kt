package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.input.NativeTextEditor
import com.pheeeew.core.designsystem.input.TextEditorSynchronization
import com.pheeeew.core.designsystem.input.rememberTextEditor
import com.pheeeew.core.designsystem.input.usesNativeTextEditor
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.device.DeviceNicknamePolicy
import com.pheeeew.feature.screens.settings.SettingsTheme
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_nickname_character_count
import pheeeew.shared.generated.resources.settings_nickname_hint

internal fun normalizeNicknameInput(value: String): String =
    buildString(value.length) {
        var index = 0
        while (index < value.length) {
            when (value[index]) {
                '\r' -> {
                    if (index + 1 < value.length && value[index + 1] == '\n') index++
                    append(' ')
                }

                '\n', '\u0085', '\u2028', '\u2029' -> {
                    append(' ')
                }

                else -> {
                    append(value[index])
                }
            }
            index++
        }
    }

internal fun acceptedNicknameInput(value: String): String? {
    val normalized = normalizeNicknameInput(value)
    return normalized.takeIf(DeviceNicknamePolicy::canAcceptInput)
}

internal fun TextFieldValue.normalizeNicknameValue(): TextFieldValue {
    val normalized = normalizeNicknameInput(text)
    if (normalized == text) return this

    fun offset(value: Int): Int = normalizeNicknameInput(text.substring(0, value)).length
    return copy(
        text = normalized,
        selection = TextRange(offset(selection.start), offset(selection.end)),
        composition = composition?.let { TextRange(offset(it.start), offset(it.end)) },
    )
}

internal val nicknameInputTransformation =
    InputTransformation {
        val next = TextFieldValue(asCharSequence().toString(), selection).normalizeNicknameValue()
        if (!DeviceNicknamePolicy.canAcceptInput(next.text)) {
            revertAllChanges()
        } else if (next.text != asCharSequence().toString()) {
            replace(0, length, next.text)
            selection = next.selection
        }
    }

@Composable
internal fun NicknameInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onImeDone: () -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (usesNativeTextEditor) {
        NativeNicknameInput(value, onValueChange, onImeDone, enabled, isError, modifier)
        return
    }
    val editor = rememberTextEditor(value, onValueChange)
    OutlinedTextField(
        state = editor.state,
        inputTransformation = nicknameInputTransformation,
        enabled = enabled,
        isError = isError,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(Res.string.settings_nickname_hint),
                color = SettingsColors.Footer,
                fontSize = 14.sp,
            )
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        supportingText = { NicknameCharacterCount(editor.state.text.toString()) },
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onImeDone() },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Border,
                unfocusedBorderColor = AppColors.BorderLight,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = SettingsColors.Ink,
                unfocusedTextColor = SettingsColors.Ink,
                cursorColor = SettingsColors.Ink,
                errorBorderColor = SettingsColors.Error,
            ),
    )
}

@Composable
private fun NativeNicknameInput(
    value: String,
    onValueChange: (String) -> Unit,
    onImeDone: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    modifier: Modifier,
) {
    var editingText by remember { mutableStateOf(value) }
    val synchronization = remember { TextEditorSynchronization(value) }
    SideEffect {
        if (synchronization.acceptExternal(value)) editingText = value
    }
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, if (isError) SettingsColors.Error else AppColors.Border, shape)
                .padding(16.dp),
        ) {
            NativeTextEditor(
                text = value,
                onTextChange = {
                    editingText = it
                    if (synchronization.publish(it)) onValueChange(it)
                },
                modifier = Modifier.fillMaxSize(),
                label = stringResource(Res.string.settings_nickname_hint),
                normalizeText = { it.normalizeNicknameValue() },
                singleLine = true,
                enabled = enabled,
                onSearch = onImeDone,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                containerColor = Color.White,
                acceptText = { acceptedNicknameInput(it) != null },
            )
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            NicknameCharacterCount(editingText)
        }
    }
}

@Composable
private fun NicknameCharacterCount(text: String) {
    Text(
        text =
            stringResource(
                Res.string.settings_nickname_character_count,
                DeviceNicknamePolicy.count(text),
                DeviceNicknamePolicy.MAX_LENGTH,
            ),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End,
        color = SettingsColors.Footer,
        fontSize = 12.sp,
    )
}

@Preview
@Composable
private fun NicknameInputFieldPreview() {
    SettingsTheme {
        NicknameInputField(value = "", onValueChange = {}, onImeDone = {})
    }
}
