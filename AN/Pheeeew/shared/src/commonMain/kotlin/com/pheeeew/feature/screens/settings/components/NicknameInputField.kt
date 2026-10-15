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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.feature.screens.settings.SettingsTheme
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_nickname_hint

@Composable
internal fun NicknameInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onImeDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(Res.string.settings_nickname_hint),
                color = SettingsColors.Footer,
                fontSize = 14.sp,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onImeDone() }),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFDADDD9),
                unfocusedBorderColor = Color(0xFFDADDD9),
                focusedContainerColor = Color(0xFFF7F8F6),
                unfocusedContainerColor = Color(0xFFF7F8F6),
                focusedTextColor = SettingsColors.Ink,
                unfocusedTextColor = SettingsColors.Ink,
                cursorColor = SettingsColors.Ink,
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
