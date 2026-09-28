package com.pheeeew.feature.screens.map.record.sheet

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.ModalBottomSheet
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
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.group.GroupSelectionStamp
import com.pheeeew.feature.screens.map.record.noRippleClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.painterResource

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
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    voiceRecorder: VoiceRecorder? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val handleDismissRequest by rememberUpdatedState<() -> Unit> {
        if (isKeyboardVisible) {
            keyboardController?.hide()
        } else {
            onDismissRequest()
        }
    }
    ModalBottomSheet(
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
        dragHandle = null,
    ) {
        RecordBottomSheetContent(
            selectedEmotion = selectedEmotion,
            inputMode = inputMode,
            memo = memo,
            selectedGroupStamp = selectedGroupStamp,
            isGroupSelectionLoading = isGroupSelectionLoading,
            onInputModeChange = onInputModeChange,
            onMemoChange = onMemoChange,
            onGroupClick = onGroupClick,
            onNext = onNext,
            onSkip = onSkip,
            modifier = Modifier,
            voiceRecorder = voiceRecorder,
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
    onSkip: () -> Unit,
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
                }.padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 94.dp, height = 3.dp)
                    .clip(AppShapes.Pill)
                    .background(AppColors.TextPrimary),
        )

        Spacer(modifier = Modifier.height(27.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(selectedEmotion.icon),
                contentDescription = selectedEmotion.label,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${selectedEmotion.recordPhrase} 마음\n${selectedEmotion.recordPrompt}",
                modifier = Modifier.weight(1f),
                lineHeight = 22.sp,
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "메모와 녹음은 선택이야.",
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .noRippleClickable(enabled = !isGroupSelectionLoading, onClick = onGroupClick),
                    contentAlignment = Alignment.Center,
                ) {
                    GroupSelectionStamp(stamp = selectedGroupStamp, size = 44.dp)
                }
                Text(
                    text = if (isGroupSelectionLoading) "그룹 확인 중" else "그룹 변경",
                    fontSize = 10.sp,
                    color = AppColors.TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            onSkip = onSkip,
            canSkip = !isGroupSelectionLoading,
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
    onSkip: () -> Unit,
    canSkip: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(AppShapes.Pill)
                    .background(AppColors.RecordSheetAction.copy(alpha = if (enabled) 1f else 0.35f))
                    .noRippleClickable(enabled = enabled, onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "다음", color = AppColors.Surface, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }
        Box(
            modifier =
                Modifier
                    .height(48.dp)
                    .noRippleClickable(enabled = canSkip, onClick = onSkip)
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "건너뛰기",
                color = AppColors.Border,
                fontSize = 14.sp,
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
                                text = "지금 마음을 짧게 적어봐",
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
                .height(45.dp)
                .clip(AppShapes.Pill)
                .background(AppColors.Gray100)
                .border(AppBorders.Standard, AppColors.Border, AppShapes.Pill)
                .padding(4.dp),
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
                    .background(AppColors.Primary),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            RecordInputModeTab(
                label = "메모",
                selected = inputMode == RecordInputModeUiModel.Memo,
                onClick = { onInputModeChange(RecordInputModeUiModel.Memo) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            RecordInputModeTab(
                label = "녹음",
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
        onSkip = {},
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
        onSkip = {},
        modifier = Modifier,
    )
}
