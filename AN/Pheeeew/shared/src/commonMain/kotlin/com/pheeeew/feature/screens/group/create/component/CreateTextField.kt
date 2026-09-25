package com.pheeeew.feature.screens.group.create.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_character_count

@Composable
internal fun CreateTextField(
    label: String,
    value: String,
    placeholder: String,
    maximumLength: Int,
    currentLength: Int,
    errorText: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 1,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = label },
            enabled = enabled,
            placeholder = { Text(placeholder, color = AppColors.RankingSecondaryContent) },
            singleLine = maxLines == 1,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            isError = errorText != null,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = imeAction,
                ),
            keyboardActions = keyboardActions,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.GroupInk,
                    unfocusedBorderColor = Color(0xFFD9DEDD),
                    errorBorderColor = Color(0xFFCA4231),
                    focusedContainerColor = Color(0xFFF8F9F9),
                    unfocusedContainerColor = Color(0xFFF8F9F9),
                    errorContainerColor = Color(0xFFFFF4F1),
                    focusedTextColor = AppColors.GroupInk,
                    unfocusedTextColor = AppColors.GroupInk,
                    errorTextColor = AppColors.GroupInk,
                    cursorColor = AppColors.GroupInk,
                ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            if (errorText != null) {
                Text(
                    text = errorText,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFCA4231),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = stringResource(Res.string.group_create_character_count, currentLength, maximumLength),
                modifier = Modifier.weight(1f),
                color = AppColors.RankingSecondaryContent,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
            )
        }
    }
}
