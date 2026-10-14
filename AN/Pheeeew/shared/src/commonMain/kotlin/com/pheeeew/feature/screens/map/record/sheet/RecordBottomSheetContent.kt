package com.pheeeew.feature.screens.map.record.sheet

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.audio.VoiceRecorder
import com.pheeeew.core.audio.VoiceRecordingState
import com.pheeeew.core.designsystem.component.AppModalBottomSheet
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.component.SheetDragHandle
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.group.GroupSelectionButton
import com.pheeeew.feature.screens.map.record.noRippleClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.record_audio
import pheeeew.shared.generated.resources.record_continue_editing
import pheeeew.shared.generated.resources.record_discard_title
import pheeeew.shared.generated.resources.record_emotion_prompt
import pheeeew.shared.generated.resources.record_excluded_input_dialog
import pheeeew.shared.generated.resources.record_memo
import pheeeew.shared.generated.resources.record_memo_prompt
import pheeeew.shared.generated.resources.record_next
import pheeeew.shared.generated.resources.record_single_attachment_hint
import pheeeew.shared.generated.resources.record_submit_selected_input

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordBottomSheet(
    selectedEmotion: EmotionTypeUiModel,
    inputMode: RecordInputModeUiModel,
    memo: String,
    selectedGroupStamp: StampAppearanceUiModel?,
    isGroupSelectionLoading: Boolean,
    onDismissRequest: () -> Unit,
    onInputModeChange: (RecordInputModeUiModel) -> Unit,
    onMemoChange: (String) -> Unit,
    onGroupClick: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    voiceRecorder: VoiceRecorder? = null,
    feedbackContent: @Composable () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var showOtherInputDialog by remember { mutableStateOf(false) }
    val handleDismissRequest by rememberUpdatedState<() -> Unit> {
        if (isKeyboardVisible) {
            keyboardController?.hide()
        } else {
            onDismissRequest()
        }
    }
    AppModalBottomSheet(
        onDismissRequest = handleDismissRequest,
        modifier = modifier,
        sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { value ->
                    if (value == SheetValue.Hidden) {
                        handleDismissRequest()
                        false
                    } else {
                        true
                    }
                },
            ),
        shape = AppShapes.BottomSheet,
        containerColor = AppColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        dragHandle = { SheetDragHandle() },
    ) {
        feedbackContent()
        RecordBottomSheetContent(
            selectedEmotion = selectedEmotion,
            inputMode = inputMode,
            memo = memo,
            selectedGroupStamp = selectedGroupStamp,
            isGroupSelectionLoading = isGroupSelectionLoading,
            onInputModeChange = onInputModeChange,
            onMemoChange = onMemoChange,
            onGroupClick = onGroupClick,
            onNext = {
                val hasOtherInput =
                    when (inputMode) {
                        RecordInputModeUiModel.Memo -> voiceRecorder?.state?.value?.filePath != null
                        RecordInputModeUiModel.Recording -> memo.isNotBlank()
                    }
                if (hasOtherInput) showOtherInputDialog = true else onNext()
            },
            modifier = Modifier,
            voiceRecorder = voiceRecorder,
        )
    }
    if (showOtherInputDialog) {
        val selectedInput =
            stringResource(
                if (inputMode ==
                    RecordInputModeUiModel.Memo
                ) {
                    Res.string.record_memo
                } else {
                    Res.string.record_audio
                },
            )
        val excludedInput =
            stringResource(
                if (inputMode ==
                    RecordInputModeUiModel.Memo
                ) {
                    Res.string.record_audio
                } else {
                    Res.string.record_memo
                },
            )
        val excludedParticle = if (inputMode == RecordInputModeUiModel.Memo) "은" else "는"
        ConfirmDialog(
            title = stringResource(Res.string.record_discard_title),
            content =
                stringResource(
                    Res.string.record_excluded_input_dialog,
                    selectedInput,
                    excludedInput,
                    excludedParticle,
                ),
            confirmText = stringResource(Res.string.record_submit_selected_input, selectedInput),
            cancelText = stringResource(Res.string.record_continue_editing),
            onConfirm = {
                showOtherInputDialog = false
                onNext()
            },
            onCancel = { showOtherInputDialog = false },
        )
    }
}

@Composable
private fun RecordBottomSheetContent(
    selectedEmotion: EmotionTypeUiModel,
    inputMode: RecordInputModeUiModel,
    memo: String,
    selectedGroupStamp: StampAppearanceUiModel?,
    isGroupSelectionLoading: Boolean,
    onInputModeChange: (RecordInputModeUiModel) -> Unit,
    onMemoChange: (String) -> Unit,
    onGroupClick: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
    voiceRecorder: VoiceRecorder? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var sheetPosition by remember { mutableStateOf(Offset.Zero) }
    var memoBounds by remember { mutableStateOf(Rect.Zero) }
    val recordingReady =
        if (inputMode == RecordInputModeUiModel.Recording) {
            rememberRecordingReady(voiceRecorder)
        } else {
            false
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(AppShapes.BottomSheet)
                .background(AppColors.Surface)
                .onGloballyPositioned { sheetPosition = it.positionInRoot() }
                .pointerInput(focusManager, keyboardController, inputMode) {
                    awaitEachGesture {
                        // Observe before child buttons consume the touch, without consuming it ourselves.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val touchedMemo =
                            inputMode == RecordInputModeUiModel.Memo &&
                                memoBounds.contains(down.position + sheetPosition)
                        if (!touchedMemo) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }
                }.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(selectedEmotion.icon),
                contentDescription = stringResource(selectedEmotion.label),
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text =
                    stringResource(
                        Res.string.record_emotion_prompt,
                        stringResource(selectedEmotion.recordPhrase),
                        stringResource(selectedEmotion.recordPrompt),
                    ),
                modifier = Modifier.weight(1f),
                lineHeight = 22.sp,
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(Res.string.record_single_attachment_hint),
            fontSize = 12.sp,
            color = AppColors.TextSecondary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordInputModeToggle(
                inputMode = inputMode,
                onInputModeChange = onInputModeChange,
                modifier = Modifier.weight(1f),
            )
            GroupSelectionButton(
                stamp = selectedGroupStamp,
                loading = isGroupSelectionLoading,
                onClick = onGroupClick,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (inputMode == RecordInputModeUiModel.Memo) {
            MemoPanel(
                memo = memo,
                onMemoChange = onMemoChange,
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { memoBounds = it.boundsInRoot() },
            )
        } else {
            RecordAudioSection(voiceRecorder = voiceRecorder)
        }
        Spacer(modifier = Modifier.height(4.dp))

        RecordSheetActions(
            onNext = onNext,
            enabled =
                !isGroupSelectionLoading &&
                    when (inputMode) {
                        RecordInputModeUiModel.Memo -> {
                            memo.isNotBlank()
                        }

                        RecordInputModeUiModel.Recording -> {
                            recordingReady
                        }
                    },
        )
    }
}

@Composable
private fun RecordAudioSection(voiceRecorder: VoiceRecorder?) {
    if (voiceRecorder == null) {
        RecordAudioContent(
            audio = VoiceRecordingState(),
            onStartRecording = {},
            onStopRecording = {},
            onPlayback = {},
            onClearRecording = {},
        )
    } else {
        val audio = voiceRecorder.state.collectAsState().value
        RecordAudioContent(
            audio = audio,
            onStartRecording = { voiceRecorder.start() },
            onStopRecording = { voiceRecorder.stop() },
            onPlayback = { voiceRecorder.togglePlayback() },
            onClearRecording = { voiceRecorder.clear() },
        )
    }
}

@Composable
private fun rememberRecordingReady(voiceRecorder: VoiceRecorder?): Boolean {
    if (voiceRecorder == null) return false
    val recordingReadyFlow =
        remember(voiceRecorder) {
            voiceRecorder.state
                .map { audio -> audio.filePath != null && !audio.recording && !audio.requestingPermission }
                .distinctUntilChanged()
        }
    val initialRecordingReady =
        voiceRecorder.state.value.let { audio ->
            audio.filePath != null && !audio.recording && !audio.requestingPermission
        }
    return recordingReadyFlow.collectAsState(initial = initialRecordingReady).value
}

@Composable
private fun RecordSheetActions(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .raisedButtonBorder(
                        AppShapes.Pill,
                        interactionSource = interactionSource,
                        elevated = enabled,
                    ).clip(AppShapes.Pill)
                    .background(if (enabled) AppColors.Primary else AppColors.Gray100)
                    .noRippleClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        onClick = onNext,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.record_next),
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MemoPanel(
    memo: String,
    onMemoChange: (String) -> Unit,
    modifier: Modifier,
) {
    Column {
        BasicTextField(
            value = memo,
            onValueChange = { value ->
                if (value.length <= 50) {
                    onMemoChange(value)
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .clip(AppShapes.Input)
                    .border(width = AppBorders.Standard, color = AppColors.Border, shape = AppShapes.Input)
                    .padding(16.dp),
            textStyle =
                LocalTextStyle.current.copy(
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (memo.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.record_memo_prompt),
                                color = AppColors.Border.copy(alpha = 0.45f),
                                fontSize = 16.sp,
                            )
                        }
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    ) {
                        innerTextField()
                    }
                }
            },
        )
        RecordInputSupportingText(
            text = "${memo.length}/50",
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RecordInputModeToggle(
    inputMode: RecordInputModeUiModel,
    onInputModeChange: (RecordInputModeUiModel) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .height(42.dp)
                .clip(AppShapes.Pill)
                .background(AppColors.Surface)
                .border(AppBorders.Standard, AppColors.Border, AppShapes.Pill),
    ) {
        val tabWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (inputMode == RecordInputModeUiModel.Memo) 0.dp else tabWidth,
            animationSpec = tween(durationMillis = 220),
            label = "recordInputModeIndicator",
        )
        Box(
            modifier =
                Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(AppShapes.Pill)
                    .background(AppColors.Primary)
                    .border(AppBorders.Standard, AppColors.Border, AppShapes.Pill),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            RecordInputModeTab(
                label = stringResource(Res.string.record_memo),
                selected = inputMode == RecordInputModeUiModel.Memo,
                onClick = { onInputModeChange(RecordInputModeUiModel.Memo) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            RecordInputModeTab(
                label = stringResource(Res.string.record_audio),
                selected = inputMode == RecordInputModeUiModel.Recording,
                onClick = { onInputModeChange(RecordInputModeUiModel.Recording) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun RecordInputModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(AppShapes.Pill)
                .semantics { this.selected = selected }
                .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "감정 기록 바텀시트", widthDp = 402, heightDp = 430)
@Composable
private fun RecordBottomSheetContentPreview() {
    RecordBottomSheetContent(
        selectedEmotion = EmotionTypeUiModel.ANGRY,
        inputMode = RecordInputModeUiModel.Memo,
        memo = "",
        selectedGroupStamp = null,
        isGroupSelectionLoading = false,
        onInputModeChange = {},
        onMemoChange = {},
        onGroupClick = {},
        onNext = {},
        modifier = Modifier,
    )
}

@Preview(name = "녹음 탭", widthDp = 402, heightDp = 430)
@Composable
private fun RecordBottomSheetRecordingPreview() {
    RecordBottomSheetContent(
        selectedEmotion = EmotionTypeUiModel.EXHAUSTED,
        inputMode = RecordInputModeUiModel.Recording,
        memo = "",
        selectedGroupStamp = null,
        isGroupSelectionLoading = false,
        onInputModeChange = {},
        onMemoChange = {},
        onGroupClick = {},
        onNext = {},
        modifier = Modifier,
    )
}
