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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.navigation.FlowBackHandler
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.feature.screens.map.overlay.MapOverlay
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.map.record.RegisteredEmotionUiModel
import com.pheeeew.feature.screens.map.record.group.GroupSelectorContent
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.location.RecordLocationSelectionContent
import com.pheeeew.feature.screens.map.record.location.RecordMapViewport
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheet
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheetUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import com.pheeeew.feature.screens.map.renderer.NativeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class PermissionDialogUiModel {
    Location,
    LocationServices,
    Microphone,
}

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    recordViewModel: MapRecordViewModel,
    onEmotionPinClick: (Long) -> Unit,
    onListClick: () -> Unit,
    onSettingClick: () -> Unit,
    onEmotionBubbleClick: (EmotionTypeUiModel) -> Unit,
    onViewportChanged: (EmotionBounds) -> Unit = {},
    locationPermissionController: LocationPermissionController,
    appSettingsLauncher: AppSettingsLauncher,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.start()
    }
    val voiceRecorder = rememberVoiceRecorder()
    val coroutineScope = rememberCoroutineScope()
    var permissionDialog by remember { mutableStateOf<PermissionDialogUiModel?>(null) }
    var isRequestingBubblePermission by remember { mutableStateOf(false) }
    LaunchedEffect(voiceRecorder) {
        voiceRecorder.refreshPermissionStatus()
        voiceRecorder.state
            .map { it.microphonePermissionDenied }
            .distinctUntilChanged()
            .collect { denied ->
                if (denied) permissionDialog = PermissionDialogUiModel.Microphone
            }
    }
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
                if (event == Lifecycle.Event.ON_STOP && !voiceRecorder.state.value.requestingPermission) {
                    voiceRecorder.stop()
                    voiceRecorder.pause()
                } else if (event == Lifecycle.Event.ON_RESUME) {
                    voiceRecorder.refreshPermissionStatus()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val uiModel by viewModel.uiModel.collectAsState()
    val recordUiModel by recordViewModel.uiModel.collectAsState()
    val groupOptions by recordViewModel.groupOptions.collectAsState()
    val notice by recordViewModel.notice.collectAsState()
    val registeredEmotion by recordViewModel.registeredEmotion.collectAsState()
    var highlightedEmotion by remember { mutableStateOf<RegisteredEmotionUiModel?>(null) }
    var highlightedPinPosition by remember { mutableStateOf<HighlightedPinPosition?>(null) }
    val clearHighlight = {
        highlightedEmotion = null
        highlightedPinPosition = null
    }
    // Keep the loaded pin's badge for one minute; discard it if its refresh never arrives.
    LaunchedEffect(highlightedEmotion?.id, highlightedPinPosition?.id) {
        if (highlightedEmotion != null) {
            delay(if (highlightedPinPosition?.id == highlightedEmotion?.id) 60_000 else 10_000)
            clearHighlight()
        }
    }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val leaveInput = {
        recordFlowCoordinator.dismiss()
        viewModel.onEmotionSelectorToggle()
    }
    val requestInputBack = {
        val audio = voiceRecorder.state.value
        if (recordUiModel.memo.isNotBlank() || audio.recording || audio.filePath != null) {
            showDiscardDialog = true
        } else {
            leaveInput()
        }
    }
    FlowBackHandler(recordUiModel.step != RecordFlowStepUiModel.Closed) {
        when {
            recordUiModel.isSubmitting -> Unit
            showDiscardDialog -> showDiscardDialog = false
            recordUiModel.isGroupSelectorVisible -> recordViewModel.onGroupSelectorDismiss()
            recordUiModel.step == RecordFlowStepUiModel.LocationSelection -> recordFlowCoordinator.backToInput()
            else -> requestInputBack()
        }
    }
    LaunchedEffect(recordUiModel.step, registeredEmotion) {
        if (recordUiModel.step == RecordFlowStepUiModel.Closed) {
            voiceRecorder.clear()
            viewModel.onRecordLocationPickingChanged(false)
            recordViewModel.consumeRegisteredEmotion()?.let { emotion ->
                highlightedPinPosition = null
                highlightedEmotion = emotion
                viewModel.focusOnCoordinate(emotion.coordinate)
                viewModel.refreshEmotionPins()
            }
        }
    }
    var recordViewport by remember { mutableStateOf<RecordMapViewport?>(null) }
    val currentLocation = (uiModel.locationState as? LocationState.Available)?.location

    LaunchedEffect(recordUiModel.step) {
        if (recordUiModel.step != RecordFlowStepUiModel.LocationSelection) recordViewport = null
        if (recordUiModel.step != RecordFlowStepUiModel.Closed) clearHighlight()
    }

    LaunchedEffect(currentLocation) {
        if (currentLocation != null) recordViewModel.onOriginLocationAvailable(currentLocation)
    }

    Box(modifier.fillMaxSize()) {
        MapScreenContent(
            uiModel = uiModel,
            recordUiModel = recordUiModel,
            voiceRecorder = voiceRecorder,
            recordViewport = recordViewport,
            onRecordCoordinateSelected = recordViewModel::onLocationSelected,
            groupOptions = groupOptions,
            mapContent = { renderUiModel, mapModifier ->
                Box(mapModifier) {
                    key(uiModel.mapRevision) {
                        NativeMap(
                            state =
                                renderUiModel.copy(
                                    recordOrigin = recordUiModel.origin,
                                    highlightedEmotionId = highlightedEmotion?.id,
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
                            onEmotionPinClick = { id ->
                                clearHighlight()
                                onEmotionPinClick(id)
                            },
                            onHighlightedPinPositionChanged = { position ->
                                highlightedPinPosition = position?.takeIf { it.id == highlightedEmotion?.id }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val highlightPosition = highlightedPinPosition
                    if (highlightPosition != null && highlightPosition.id == highlightedEmotion?.id &&
                        uiModel.mapError == null
                    ) {
                        val position = highlightPosition
                        key(position.id) {
                            RegisteredPinHighlight(position = position)
                        }
                    }
                }
            },
            onListClick = onListClick,
            onSettingClick = onSettingClick,
            onEmotionSelectorToggle = viewModel::onEmotionSelectorToggle,
            onEmotionBubbleClick = { emotion ->
                if (!isRequestingBubblePermission) {
                    isRequestingBubblePermission = true
                    coroutineScope.launch {
                        try {
                            val status = locationPermissionController.currentStatus()
                            val result =
                                if (status == LocationPermissionStatus.Granted ||
                                    status == LocationPermissionStatus.ServicesDisabled
                                ) {
                                    status
                                } else {
                                    locationPermissionController.requestPermission()
                                }
                            when (result) {
                                LocationPermissionStatus.Granted -> {
                                    viewModel.onMyLocationClick()
                                    recordFlowCoordinator.onEmotionSelected(emotion)
                                    onEmotionBubbleClick(emotion)
                                }

                                LocationPermissionStatus.ServicesDisabled -> {
                                    permissionDialog = PermissionDialogUiModel.LocationServices
                                }

                                else -> {
                                    permissionDialog = PermissionDialogUiModel.Location
                                }
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            permissionDialog = PermissionDialogUiModel.Location
                        } finally {
                            isRequestingBubblePermission = false
                        }
                    }
                }
            },
            onMyLocationClick = viewModel::onMyLocationClick,
            onRetryMap = viewModel::retryMap,
            onRecordBottomSheetDismiss = requestInputBack,
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
            modifier = Modifier.fillMaxSize(),
        )
        Snackbar(
            message = notice?.message,
            onDismiss = recordViewModel::dismissNotice,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp),
            isError = notice?.isError == true,
        )
    }
    if (showDiscardDialog) {
        ConfirmDialog(
            title = "작성중인 내용이 있습니다.",
            content = "지금까지 작성하던 내용이 저장되지 않습니다.\n나가시겠습니까?",
            confirmText = "나가기",
            cancelText = "취소",
            onConfirm = {
                showDiscardDialog = false
                leaveInput()
            },
            onCancel = { showDiscardDialog = false },
        )
    }
    permissionDialog?.let { dialog ->
        ConfirmDialog(
            title =
                when (dialog) {
                    PermissionDialogUiModel.Location -> "위치 권한이 필요해요"
                    PermissionDialogUiModel.LocationServices -> "위치 서비스를 켜주세요"
                    PermissionDialogUiModel.Microphone -> "마이크 권한이 필요해요"
                },
            content =
                when (dialog) {
                    PermissionDialogUiModel.Location -> "감정을 남기려면 위치 권한을 허용해 주세요.\n설정에서 권한을 켤 수 있어요."
                    PermissionDialogUiModel.LocationServices -> "감정을 남기려면 위치 서비스를 켜주세요."
                    PermissionDialogUiModel.Microphone -> "음성을 녹음하려면 마이크 권한을 허용해 주세요.\n설정에서 권한을 켤 수 있어요."
                },
            confirmText = "설정으로 이동",
            cancelText = "취소",
            onConfirm = {
                permissionDialog = null
                if (dialog == PermissionDialogUiModel.Microphone) voiceRecorder.clear()
                coroutineScope.launch {
                    if (dialog == PermissionDialogUiModel.LocationServices) {
                        appSettingsLauncher.openLocationSettings()
                    } else {
                        appSettingsLauncher.openAppSettings()
                    }
                }
            },
            onCancel = {
                permissionDialog = null
                if (dialog == PermissionDialogUiModel.Microphone) voiceRecorder.clear()
            },
        )
    }
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
                    selectedGroupStamp = groupOptions.firstOrNull { it.id == recordUiModel.selectedGroupId }?.stamp,
                    isGroupSelectionLoading = recordUiModel.isGroupSelectionLoading,
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
                    selectedEmotion = recordUiModel.selectedEmotion ?: EmotionTypeUiModel.FRUSTRATED,
                    selectedGroupStamp = groupOptions.firstOrNull { it.id == recordUiModel.selectedGroupId }?.stamp,
                    isSubmitting = recordUiModel.isSubmitting,
                    canConfirm =
                        recordUiModel.isSelectedCoordinateInRange && recordViewport != null &&
                            uiModel.mapError == null && !recordUiModel.isSubmitting,
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
                GroupSelectorGroupUiModel("none", "없음", null),
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
