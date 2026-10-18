package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.device.DeviceNicknamePolicy
import com.pheeeew.feature.screens.settings.SettingsTheme
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_nickname_character_count
import pheeeew.shared.generated.resources.settings_nickname_hint

@Composable
internal fun NicknameInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onImeDone: () -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (DeviceNicknamePolicy.canAcceptInput(it)) onValueChange(it) },
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
        singleLine = false,
        maxLines = 3,
        supportingText = {
            Text(
                text =
                    stringResource(
                        Res.string.settings_nickname_character_count,
                        DeviceNicknamePolicy.count(value),
                        DeviceNicknamePolicy.MAX_LENGTH,
                    ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                color = SettingsColors.Footer,
                fontSize = 12.sp,
            )
        },
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onImeDone() }),
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

@Preview
@Composable
private fun NicknameInputFieldPreview() {
    SettingsTheme {
        NicknameInputField(value = "", onValueChange = {}, onImeDone = {})
    }
}
