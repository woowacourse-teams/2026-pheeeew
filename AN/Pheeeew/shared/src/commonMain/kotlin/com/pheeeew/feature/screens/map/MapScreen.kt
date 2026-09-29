package com.pheeeew.feature.screens.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.core.audio.VoiceRecorder
import com.pheeeew.core.audio.rememberVoiceRecorder
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.navigation.FlowBackHandler
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.feature.screens.map.detail.EmotionDetailLoadUiModel
import com.pheeeew.feature.screens.map.overlay.MapFeedbackOverlay
import com.pheeeew.feature.screens.map.overlay.MapOverlay
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.map.record.RecordConnectionNotice
import com.pheeeew.feature.screens.map.record.RegisteredEmotionUiModel
import com.pheeeew.feature.screens.map.record.group.GroupSelectorContent
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.group.groupSelectorDialogProperties
import com.pheeeew.feature.screens.map.record.location.RecordLocationSelectionContent
import com.pheeeew.feature.screens.map.record.location.RecordMapViewport
import com.pheeeew.feature.screens.map.record.rememberRecordConnectionMessage
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheet
import com.pheeeew.feature.screens.map.record.sheet.RecordBottomSheetUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import com.pheeeew.feature.screens.map.renderer.NativeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    message: String? = null,
    onMessageDismiss: () -> Unit = {},
    detailError: EmotionDetailLoadUiModel.Failed? = null,
    onRetryDetail: () -> Unit = {},
    onDismissDetailError: () -> Unit = {},
    monitoringVisible: Boolean = true,
) {
    LaunchedEffect(viewModel) {
        viewModel.onMapRendererAttached()
        viewModel.start()
    }
    val voiceRecorder =
        com.pheeeew.feature.screens.map.monitoring.rememberMonitoredVoiceRecorder(
            recordViewModel.funnel,
        )
    val coroutineScope = rememberCoroutineScope()
    val pinPressScale = remember { Animatable(1f) }
    var pressedPinId by remember { mutableStateOf<Long?>(null) }
    var pinPressJob by remember { mutableStateOf<Job?>(null) }
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
    var monitoringResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner, recordViewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) monitoringResumed = true
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) monitoringResumed = false
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recordViewModel.funnel.screenHidden()
        }
    }
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
    val contentVisible =
        monitoringResumed && monitoringVisible && uiModel.mapError == null &&
            !uiModel.isEmotionSelectorExpanded && recordUiModel.step == RecordFlowStepUiModel.Closed &&
            permissionDialog == null
    DisposableEffect(viewModel, contentVisible) {
        viewModel.contentVisibility(contentVisible)
        onDispose { viewModel.contentVisibility(false) }
    }
    val notice by recordViewModel.notice.collectAsState()
    val registeredEmotion by recordViewModel.registeredEmotion.collectAsState()
    var highlightedEmotion by remember { mutableStateOf<RegisteredEmotionUiModel?>(null) }
    var highlightedPinPosition by remember { mutableStateOf<HighlightedPinPosition?>(null) }
    val highlightedId = uiModel.focusedEmotionId ?: highlightedEmotion?.id
    val clearHighlight = {
        highlightedEmotion = null
        highlightedPinPosition = null
        viewModel.clearFocusedEmotion()
    }
    LaunchedEffect(uiModel.focusedEmotionId) {
        if (uiModel.focusedEmotionId != null) highlightedEmotion = null
    }
    // Keep the loaded pin's badge for one minute; discard it if its refresh never arrives.
    LaunchedEffect(highlightedEmotion?.id, highlightedPinPosition?.id) {
        if (highlightedEmotion != null) {
            delay(if (highlightedPinPosition?.id == highlightedEmotion?.id) 60_000 else 10_000)
            clearHighlight()
        }
    }
    LaunchedEffect(recordViewModel, monitoringResumed, monitoringVisible) {
        if (monitoringResumed && monitoringVisible) {
            recordViewModel.funnel.screenShown()
        } else {
            recordViewModel.funnel.screenHidden()
        }
    }
    LaunchedEffect(
        recordViewModel,
        monitoringResumed,
        monitoringVisible,
        uiModel.isEmotionSelectorExpanded,
        recordUiModel.step,
        isRequestingBubblePermission,
    ) {
        recordViewModel.funnel.selectorVisibility(
            monitoringResumed && monitoringVisible && !isRequestingBubblePermission &&
                uiModel.isEmotionSelectorExpanded &&
                recordUiModel.step == RecordFlowStepUiModel.Closed,
        )
    }
    LaunchedEffect(
        recordViewModel,
        monitoringResumed,
        monitoringVisible,
        recordUiModel.step,
        recordUiModel.isGroupSelectorVisible,
    ) {
        recordViewModel.funnel.stepShown(
            if (!monitoringResumed || !monitoringVisible) {
                null
            } else {
                when {
                    recordUiModel.isGroupSelectorVisible -> "group"
                    recordUiModel.step == RecordFlowStepUiModel.Input -> "input"
                    recordUiModel.step == RecordFlowStepUiModel.LocationSelection -> "location"
                    else -> null
                }
            },
        )
    }
    LaunchedEffect(voiceRecorder, recordViewModel) {
        voiceRecorder.state.map { it.recording }.distinctUntilChanged().collect { recording ->
            if (recording) recordViewModel.funnel.inputStarted("voice")
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
    FlowBackHandler(
        enabled = uiModel.isEmotionSelectorExpanded,
        onBack = viewModel::onEmotionSelectorToggle,
    )
    FlowBackHandler(recordUiModel.step != RecordFlowStepUiModel.Closed) {
        when {
            recordUiModel.isSubmitting -> Unit
            showDiscardDialog -> showDiscardDialog = false
            recordUiModel.isGroupSelectorVisible -> recordViewModel.onGroupSelectorDismiss()
            recordUiModel.step == RecordFlowStepUiModel.LocationSelection -> recordFlowCoordinator.backToInput()
            else -> requestInputBack()
        }
    }
    LaunchedEffect(recordViewModel, viewModel) {
        recordViewModel.registeredEmotions.collect(viewModel::onEmotionRegistered)
    }
    LaunchedEffect(recordUiModel.step, registeredEmotion) {
        if (recordUiModel.step == RecordFlowStepUiModel.Closed) {
            voiceRecorder.clear()
            viewModel.onRecordLocationPickingChanged(false)
            recordViewModel.consumeRegisteredEmotion()?.let { emotion ->
                highlightedPinPosition = null
                highlightedEmotion = emotion
                viewModel.focusOnCoordinate(emotion.coordinate)
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
                                    highlightedEmotionId = highlightedId,
                                    pressedEmotionId = pressedPinId,
                                    pressedEmotionScale = pinPressScale.value,
                                    emotionContentLoad = renderUiModel.emotionContentLoad.takeIf { contentVisible },
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
                                pinPressJob?.cancel()
                                pinPressJob =
                                    coroutineScope.launch {
                                        pressedPinId = id
                                        pinPressScale.snapTo(1f)
                                        pinPressScale.animateTo(0.9f, tween(durationMillis = 70))
                                        pinPressScale.animateTo(1.1f, tween(durationMillis = 110))
                                        pinPressScale.animateTo(1f, tween(durationMillis = 100))
                                        pressedPinId = null
                                        onEmotionPinClick(id)
                                    }
                            },
                            onContentPresented = viewModel::contentPresented,
                            onHighlightedPinPositionChanged = { position ->
                                highlightedPinPosition = position?.takeIf { it.id == highlightedId }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val highlightPosition = highlightedPinPosition
                    if (highlightPosition != null && highlightPosition.id == highlightedId &&
                        uiModel.mapError == null
                    ) {
                        val position = highlightPosition
                        val focused = uiModel.focusedEmotionId == position.id
                        key(position.id, uiModel.cameraCommand?.id.takeIf { focused }) {
                            RegisteredPinHighlight(
                                position = position,
                                showBadge = !focused,
                                repeatPulse = focused,
                                scale = if (focused) 1.3f else 1f,
                            )
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
                    val selectorId = recordViewModel.funnel.selected()
                    coroutineScope.launch {
                        var requestedPermission = false
                        try {
                            val status = locationPermissionController.currentStatus()
                            val result =
                                if (status == LocationPermissionStatus.Granted ||
                                    status == LocationPermissionStatus.ServicesDisabled
                                ) {
                                    status
                                } else {
                                    requestedPermission = true
                                    locationPermissionController.requestPermission().also { permission ->
                                        recordViewModel.funnel.permissionFinished(
                                            selectorId,
                                            when (permission) {
                                                LocationPermissionStatus.Granted -> "granted"
                                                LocationPermissionStatus.ServicesDisabled -> "services_disabled"
                                                else -> "denied"
                                            },
                                        )
                                    }
                                }
                            requestedPermission = false
                            when (result) {
                                LocationPermissionStatus.Granted -> {
                                    viewModel.onMyLocationClick(fromUser = false)
                                    recordFlowCoordinator.onEmotionSelected(emotion, selectorId)
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
                            if (requestedPermission) recordViewModel.funnel.permissionFinished(selectorId, "cancelled")
                            throw cancellation
                        } catch (_: Exception) {
                            if (requestedPermission) recordViewModel.funnel.permissionFinished(selectorId, "unknown")
                            permissionDialog = PermissionDialogUiModel.Location
                        } finally {
                            isRequestingBubblePermission = false
                        }
                    }
                }
            },
            onMyLocationClick = viewModel::onMyLocationClick,
            onMapRefreshClick = viewModel::refreshEmotionPins,
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
        MapFeedbackOverlay(
            uiModel = uiModel,
            suppressConnectionFeedback = recordUiModel.step != RecordFlowStepUiModel.Closed,
            notice = notice,
            onDismissNotice = recordViewModel::dismissNotice,
            message = message,
            onMessageDismiss = onMessageDismiss,
            detailError = detailError,
            onRetryDetail = onRetryDetail,
            onDismissDetailError = onDismissDetailError,
            onAction = { action ->
                when (action) {
                    MapFeedbackAction.RetryMap -> {
                        viewModel.retryMap()
                    }

                    MapFeedbackAction.RetryPins -> {
                        viewModel.retryEmotionPins()
                    }

                    MapFeedbackAction.RetryLocation -> {
                        viewModel.onMyLocationClick()
                    }

                    MapFeedbackAction.OpenSettings -> {
                        coroutineScope.launch { appSettingsLauncher.openAppSettings() }
                    }

                    MapFeedbackAction.OpenLocationSettings -> {
                        coroutineScope.launch {
                            appSettingsLauncher
                                .openLocationSettings()
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    if (showDiscardDialog) {
        ConfirmDialog(
            title = "작성 중인 내용이 있어요.",
            content = "지금까지 작성하던 내용이 저장되지 않아요.\n나갈까요?",
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
    onMapRefreshClick: () -> Unit,
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
    val connectionMessage =
        rememberRecordConnectionMessage(
            active = recordUiModel.step != RecordFlowStepUiModel.Closed,
            isOffline = uiModel.isOffline,
        )
    Box(modifier = modifier.fillMaxSize()) {
        val symbolImages = rememberEmotionPinSymbolImages(uiModel.emotionPins)
        mapContent(
            uiModel.copy(emotionPinSymbolImages = symbolImages),
            Modifier.fillMaxSize(),
        )

        if (recordUiModel.step != RecordFlowStepUiModel.LocationSelection) {
            MapOverlay(
                showNavigationButtons = recordUiModel.step == RecordFlowStepUiModel.Closed,
                onListClick = onListClick,
                onSettingClick = onSettingClick,
                isEmotionSelectorExpanded = uiModel.isEmotionSelectorExpanded,
                onEmotionSelectorToggle = onEmotionSelectorToggle,
                onEmotionBubbleClick = onEmotionBubbleClick,
                onRefreshClick = onMapRefreshClick,
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
                    connectionMessage = connectionMessage.takeUnless { recordUiModel.isGroupSelectorVisible },
                )
                if (recordUiModel.isGroupSelectorVisible) {
                    Dialog(
                        onDismissRequest = onRecordGroupSelectorDismiss,
                        properties = groupSelectorDialogProperties(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
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
                            RecordConnectionNotice(connectionMessage)
                        }
                    }
                }
            }

            RecordFlowStepUiModel.LocationSelection -> {
                RecordLocationSelectionContent(
                    showDragGuide = !recordUiModel.hasMovedStamp,
                    onMyLocationClick = onMyLocationClick,
                    isRequestingLocation = uiModel.isRequestingLocation,
                    origin = recordUiModel.origin,
                    selectedCoordinate = recordUiModel.selectedCoordinate,
                    viewport = recordViewport,
                    onCoordinateSelected = onRecordCoordinateSelected,
                    selectedEmotion = recordUiModel.selectedEmotion ?: EmotionTypeUiModel.FRUSTRATED,
                    selectedGroupStamp = groupOptions.firstOrNull { it.id == recordUiModel.selectedGroupId }?.stamp,
                    isSubmitting = recordUiModel.isSubmitting,
                    canConfirm =
                        recordUiModel.isSelectedCoordinateInRange && recordViewport != null &&
                            uiModel.mapError == null && !recordUiModel.isSubmitting && !uiModel.isOffline,
                    onConfirm = onRecordConfirmLocation,
                    onBack = onRecordBackToInput,
                    modifier = Modifier.fillMaxSize(),
                )
                RecordConnectionNotice(connectionMessage)
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
        onMapRefreshClick = {},
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
