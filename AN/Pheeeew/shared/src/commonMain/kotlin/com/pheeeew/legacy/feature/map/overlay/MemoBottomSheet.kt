package com.pheeeew.legacy.feature.map.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.component.ConfirmDialog
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import com.pheeeew.legacy.core.navigation.PredictiveBackEffect
import com.pheeeew.legacy.feature.map.MAX_MEMO_LENGTH
import com.pheeeew.legacy.feature.map.PendingSighDraft
import com.pheeeew.legacy.feature.map.guide.FirstSighGuideBubble
import com.pheeeew.legacy.feature.map.guide.FirstSighGuideStep
import kotlinx.coroutines.flow.first

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun MemoEditor(
    draft: PendingSighDraft,
    submitting: Boolean,
    guideMode: Boolean,
    onShown: () -> Unit,
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val value =
        rememberSaveable(
            draft.requestId,
            saver = TextFieldState.Saver,
        ) { TextFieldState() }
    var showDiscardDialog by rememberSaveable(draft.requestId) { mutableStateOf(false) }

    MemoBottomSheet(
        value = value,
        submitting = submitting,
        guideMode = guideMode,
        onShown = onShown,
        onSubmit = { onSubmit(value.text.toString()) },
        onSkip = onSkip,
        onDismiss = {
            if (value.text.isEmpty()) {
                onDismiss()
            } else {
                showDiscardDialog = true
            }
        },
        dismissConfirmationVisible = showDiscardDialog,
    )

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "작성중인 내용이 있습니다.",
            body = "지금까지 작성하던 내용이 저장되지 않습니다. 나가시겠습니까?",
            confirmText = "나가기",
            onConfirmClick = {
                showDiscardDialog = false
                onDismiss()
            },
            onDismissRequest = { showDiscardDialog = false },
            onDismissClick = { showDiscardDialog = false },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
private fun MemoBottomSheet(
    value: TextFieldState,
    submitting: Boolean,
    guideMode: Boolean,
    onShown: () -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    dismissConfirmationVisible: Boolean,
) {
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    val latestSubmitting = rememberUpdatedState(submitting)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                if (value == SheetValue.Hidden && !latestSubmitting.value) {
                    latestOnDismiss.value()
                    false
                } else {
                    value != SheetValue.Hidden || !latestSubmitting.value
                }
            },
        )
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }
            .first { it != SheetValue.Hidden }
        onShown()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = AppColors.Cream100,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null,
    ) {
        if (!dismissConfirmationVisible) {
            PredictiveBackEffect(
                onProgress = {},
                onCompleted = { latestOnDismiss.value() },
                onCancelled = {},
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (guideMode) {
                FirstSighGuideBubble(step = FirstSighGuideStep.Memo)
                Spacer(modifier = Modifier.height(16.dp))
            }
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = AppColors.Navy800,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ).imePadding()
                        .navigationBarsPadding(),
            ) {
                BottomSheetDefaults.DragHandle(
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .padding(start = 26.dp, top = 52.dp, end = 26.dp, bottom = 24.dp),
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
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(weight = 1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        BasicTextField(
                            state = value,
                            enabled = !submitting,
                            inputTransformation = InputTransformation.maxLength(MAX_MEMO_LENGTH),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 184.dp)
                                    .background(AppColors.Navy700, RoundedCornerShape(14.dp))
                                    .padding(16.dp),
                            textStyle = AppTheme.typography.dialogBody.copy(color = AppColors.Cream100),
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Default,
                                ),
                            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                            decorator =
                                TextFieldDecorator { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (value.text.isEmpty()) {
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
                                text = "${value.text.length}/$MAX_MEMO_LENGTH",
                                style = AppTheme.typography.caption,
                                color = AppColors.Cream100.copy(alpha = 0.6f),
                            )
                        }
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
    }
}
