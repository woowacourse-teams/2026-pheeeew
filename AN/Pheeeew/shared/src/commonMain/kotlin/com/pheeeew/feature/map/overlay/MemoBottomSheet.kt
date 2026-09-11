package com.pheeeew.feature.map.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.map.MAX_MEMO_LENGTH
import com.pheeeew.feature.map.PendingSighDraft

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MemoEditor(
    draft: PendingSighDraft,
    submitting: Boolean,
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(draft.requestId) { mutableStateOf("") }
    MemoBottomSheet(
        value = value,
        onValueChange = { nextValue ->
            if (nextValue.length <= MAX_MEMO_LENGTH) value = nextValue
        },
        submitting = submitting,
        onSubmit = { onSubmit(value) },
        onSkip = onSkip,
        onDismiss = onDismiss,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MemoBottomSheet(
    value: String,
    onValueChange: (String) -> Unit,
    submitting: Boolean,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value -> value != SheetValue.Hidden || !submitting },
        )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Navy800,
        contentColor = AppColors.Cream100,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 26.dp, top = 4.dp, end = 26.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "한숨에 담아 보낼 마음",
                style = AppTheme.typography.screenTitle,
            )
            Text(
                text = "메모는 선택사항이에요",
                style = AppTheme.typography.dialogBody,
                color = AppColors.Cream100.copy(alpha = 0.7f),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !submitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(184.dp)
                        .background(AppColors.Navy700, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                textStyle =
                    TextStyle(
                        color = AppColors.Cream100,
                        fontSize = AppTheme.typography.dialogBody.fontSize,
                    ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = "오늘 어떤 일이 있었나요?",
                                style = AppTheme.typography.dialogBody,
                                color = AppColors.Cream100.copy(alpha = 0.4f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "${value.length}/$MAX_MEMO_LENGTH",
                    style = AppTheme.typography.caption,
                    color = AppColors.Cream100.copy(alpha = 0.6f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(25.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AppColors.Blue100,
                            contentColor = AppColors.Navy800,
                        ),
                ) {
                    Text(text = "다음", style = AppTheme.typography.button)
                }
                TextButton(onClick = onSkip, enabled = !submitting) {
                    Text(
                        text = "건너뛰기",
                        style = AppTheme.typography.button,
                        color = AppColors.Cream100.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
