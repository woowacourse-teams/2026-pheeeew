package com.pheeeew.feature.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.core.audio.VoiceRecorder
import com.pheeeew.core.audio.rememberVoiceRecorder
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.feature.screens.map.overlay.MapOverlay
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.map.record.group.GroupSelectorContent
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.location.RecordLocationSelectionContent
import com.pheeeew.feature.screens.map.record.location.RecordMapViewport
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheet
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheetUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import com.pheeeew.feature.screens.map.renderer.NativeMap

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    recordViewModel: MapRecordViewModel,
    onEmotionPinClick: (Long) -> Unit,
    onListClick: () -> Unit,
    onSettingClick: () -> Unit,
    onEmotionBubbleClick: (EmotionTypeUiModel) -> Unit,
    onViewportChanged: (EmotionBounds) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.start()
    }
    val voiceRecorder = rememberVoiceRecorder()
    val recordFlowCoordinator =
        remember(viewModel, recordViewModel, voiceRecorder) {
            MapRecordFlowCoordinator(
                mapViewModel = viewModel,
                recordViewModel = recordViewModel,
                voiceRecorder = voiceRecorder,
            )
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, voiceRecorder) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    voiceRecorder.stop()
                    voiceRecorder.pause()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val uiModel by viewModel.uiModel.collectAsState()
    val recordUiModel by recordViewModel.uiModel.collectAsState()
    var recordViewport by remember { mutableStateOf<RecordMapViewport?>(null) }
    val currentLocation = (uiModel.locationState as? LocationState.Available)?.location

    LaunchedEffect(recordUiModel.step) {
        if (recordUiModel.step != RecordFlowStepUiModel.LocationSelection) recordViewport = null
    }

    LaunchedEffect(currentLocation) {
        if (currentLocation != null) recordViewModel.onOriginLocationAvailable(currentLocation)
    }

    MapScreenContent(
        uiModel = uiModel,
        recordUiModel = recordUiModel,
        voiceRecorder = voiceRecorder,
        recordViewport = recordViewport,
        onRecordCoordinateSelected = recordViewModel::onLocationSelected,
        groupOptions = recordViewModel.groupOptions,
        mapContent = { renderUiModel, mapModifier ->
            key(uiModel.mapRevision) {
                NativeMap(
                    state =
                        renderUiModel.copy(
                            recordOrigin = recordUiModel.origin,
                            emotionPins =
                                renderUiModel.emotionPins.filterNot {
                                    it.id in renderUiModel.hiddenEmotionIds
                                },
                        ),
                    onMapError = viewModel::onMapError,
                    onMapRecovered = viewModel::onMapRecovered,
                    onRecordViewportChanged = { centerX, centerY, radius ->
                        val viewport = RecordMapViewport(centerX, centerY, radius)
                        if (recordViewport != viewport) recordViewport = viewport
                    },
                    onViewportChanged = { bounds ->
                        viewModel.onViewportChanged(bounds)
                        onViewportChanged(
                            EmotionBounds(
                                bounds.minLongitude,
                                bounds.minLatitude,
                                bounds.maxLongitude,
                                bounds.maxLatitude,
                            ),
                        )
                    },
                    onEmotionPinClick = onEmotionPinClick,
                    modifier = mapModifier,
                )
            }
        },
        onListClick = onListClick,
        onSettingClick = onSettingClick,
        onEmotionSelectorToggle = viewModel::onEmotionSelectorToggle,
        onEmotionBubbleClick = { emotion ->
            recordFlowCoordinator.onEmotionSelected(emotion)
            onEmotionBubbleClick(emotion)
        },
        onMyLocationClick = viewModel::onMyLocationClick,
        onRetryMap = viewModel::retryMap,
        onRecordBottomSheetDismiss = recordFlowCoordinator::dismiss,
        onRecordInputModeChange = recordFlowCoordinator::changeInputMode,
        onRecordMemoChange = recordViewModel::onMemoChange,
        onRecordGroupClick = recordViewModel::onGroupSelectorOpen,
        onRecordNext = { recordFlowCoordinator.next(currentLocation) },
        onRecordSkip = { recordFlowCoordinator.skip(currentLocation) },
        onRecordBackToInput = recordFlowCoordinator::backToInput,
        onRecordConfirmLocation = recordFlowCoordinator::confirmLocation,
        onRecordGroupSelectorDismiss = recordViewModel::onGroupSelectorDismiss,
        onRecordGroupDialProgressChange = recordViewModel::onGroupDialProgressChange,
        onRecordGroupDialProgressSettle = recordViewModel::onGroupDialProgressSettle,
        onRecordPendingGroupChange = recordViewModel::onPendingGroupChange,
        onRecordGroupSelectionComplete = recordViewModel::onGroupSelectionComplete,
        modifier = modifier,
    )
}

@Composable
internal fun MapScreenContent(
    uiModel: MapUiModel,
    recordUiModel: RecordBottomSheetUiModel,
    groupOptions: List<GroupSelectorGroupUiModel>,
    mapContent: @Composable (MapUiModel, Modifier) -> Unit,
    onListClick: () -> Unit,
    onSettingClick: () -> Unit,
    onEmotionSelectorToggle: () -> Unit,
    onEmotionBubbleClick: (EmotionTypeUiModel) -> Unit,
    onMyLocationClick: () -> Unit,
    onRetryMap: () -> Unit,
    onRecordBottomSheetDismiss: () -> Unit,
    onRecordInputModeChange: (RecordInputModeUiModel) -> Unit,
    onRecordMemoChange: (String) -> Unit,
    onRecordGroupClick: () -> Unit,
    onRecordNext: () -> Unit,
    onRecordSkip: () -> Unit,
    onRecordBackToInput: () -> Unit,
    onRecordConfirmLocation: () -> Unit,
    onRecordGroupSelectorDismiss: () -> Unit,
    onRecordGroupDialProgressChange: (Float) -> Unit,
    onRecordGroupDialProgressSettle: (Float) -> Unit,
    onRecordPendingGroupChange: (GroupSelectorGroupUiModel) -> Unit,
    onRecordGroupSelectionComplete: (GroupSelectorGroupUiModel) -> Unit,
    modifier: Modifier,
    voiceRecorder: VoiceRecorder? = null,
    recordViewport: RecordMapViewport? = null,
    onRecordCoordinateSelected: (Double, Double) -> Unit = { _, _ -> },
) {
    Box(modifier = modifier.fillMaxSize()) {
        val symbolImages = rememberEmotionPinSymbolImages(uiModel.emotionPins)
        mapContent(
            uiModel.copy(emotionPinSymbolImages = symbolImages),
            Modifier.fillMaxSize(),
        )

        if (uiModel.mapError != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("지도를 불러오지 못했어요", color = Color.White)
                    Button(
                        onClick = onRetryMap,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White,
                            ),
                    ) {
                        Text("다시 시도")
                    }
                }
            }
        }

        if (recordUiModel.step != RecordFlowStepUiModel.LocationSelection) {
            MapOverlay(
                onListClick = onListClick,
                onSettingClick = onSettingClick,
                isEmotionSelectorExpanded = uiModel.isEmotionSelectorExpanded,
                onEmotionSelectorToggle = onEmotionSelectorToggle,
                onEmotionBubbleClick = onEmotionBubbleClick,
                onMyLocationClick = onMyLocationClick,
                isRequestingLocation = uiModel.isRequestingLocation,
                isMapError = uiModel.mapError != null,
            )
        }

        when (recordUiModel.step) {
            RecordFlowStepUiModel.Closed -> {}

            RecordFlowStepUiModel.Input -> {
                RecordBottomSheet(
                    onDismissRequest = onRecordBottomSheetDismiss,
                    selectedEmotion = recordUiModel.selectedEmotion ?: EmotionTypeUiModel.FRUSTRATED,
                    inputMode = recordUiModel.inputMode,
                    memo = recordUiModel.memo,
                    voiceRecorder = voiceRecorder,
                    groupLabel = recordUiModel.groupLabel,
                    onInputModeChange = onRecordInputModeChange,
                    onMemoChange = onRecordMemoChange,
                    onGroupClick = onRecordGroupClick,
                    onNext = onRecordNext,
                    onSkip = onRecordSkip,
                )
                if (recordUiModel.isGroupSelectorVisible) {
                    Dialog(
                        onDismissRequest = onRecordGroupSelectorDismiss,
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        GroupSelectorContent(
                            isVisible = true,
                            groups = groupOptions,
                            selectedGroupId = recordUiModel.pendingGroupId,
                            dialProgress = recordUiModel.groupDialProgress,
                            onDialProgressChange = onRecordGroupDialProgressChange,
                            onDialProgressSettle = onRecordGroupDialProgressSettle,
                            onSelectedGroupChange = onRecordPendingGroupChange,
                            onDismiss = onRecordGroupSelectorDismiss,
                            onComplete = onRecordGroupSelectionComplete,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            RecordFlowStepUiModel.LocationSelection -> {
                RecordLocationSelectionContent(
                    origin = recordUiModel.origin,
                    selectedCoordinate = recordUiModel.selectedCoordinate,
                    viewport = recordViewport,
                    onCoordinateSelected = onRecordCoordinateSelected,
                    groupLabel = recordUiModel.groupLabel,
                    locationMessage = recordUiModel.locationMessage,
                    submissionMessage = recordUiModel.submissionMessage,
                    canConfirm =
                        recordUiModel.isSelectedCoordinateInRange && recordViewport != null &&
                            uiModel.mapError == null && recordUiModel.confirmedRecord == null,
                    onConfirm = onRecordConfirmLocation,
                    onBack = onRecordBackToInput,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(name = "지도 화면", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun MapScreenContentPreview() {
    MapScreenContent(
        uiModel = MapUiModel(),
        recordUiModel = RecordBottomSheetUiModel(),
        groupOptions =
            listOf(
                GroupSelectorGroupUiModel("personal", "개인", "개인"),
                GroupSelectorGroupUiModel("none", "그룹 없음", "없음"),
            ),
        mapContent = { _, modifier -> Box(modifier.background(Color(0xFFECEAE5))) },
        onListClick = {},
        onSettingClick = {},
        onEmotionSelectorToggle = {},
        onEmotionBubbleClick = {},
        onMyLocationClick = {},
        onRetryMap = {},
        onRecordBottomSheetDismiss = {},
        onRecordInputModeChange = {},
        onRecordMemoChange = {},
        onRecordGroupClick = {},
        onRecordNext = {},
        onRecordSkip = {},
        onRecordBackToInput = {},
        onRecordConfirmLocation = {},
        onRecordGroupSelectorDismiss = {},
        onRecordGroupDialProgressChange = {},
        onRecordGroupDialProgressSettle = {},
        onRecordPendingGroupChange = {},
        onRecordGroupSelectionComplete = {},
        modifier = Modifier.fillMaxSize(),
    )
}
