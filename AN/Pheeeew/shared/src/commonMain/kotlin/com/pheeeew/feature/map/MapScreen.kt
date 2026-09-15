package com.pheeeew.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.audio.BreathInputError
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.permission.LocationPermissionSettingsDialog
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.core.permission.LocationServicesSettingsDialog
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.feature.map.animation.SighAnimationCoordinator
import com.pheeeew.feature.map.animation.StarFlightOverlay
import com.pheeeew.feature.map.guide.FirstSighGuideOverlay
import com.pheeeew.feature.map.guide.FirstSighGuideStep
import com.pheeeew.feature.map.guide.FirstSighSwipeOverlay
import com.pheeeew.feature.map.guide.firstSighGuideStepFor
import com.pheeeew.feature.map.map.BreathMap
import com.pheeeew.feature.map.map.MapError
import com.pheeeew.feature.map.map.MapProjectionSnapshot
import com.pheeeew.feature.map.overlay.BreathControl
import com.pheeeew.feature.map.overlay.ErrorSnackbar
import com.pheeeew.feature.map.overlay.MapOverlay
import com.pheeeew.feature.map.overlay.MemoEditor
import com.pheeeew.feature.map.overlay.SighPhase
import com.pheeeew.feature.map.sighlist.SIGH_BROWSER_EXIT_DURATION_MILLIS
import com.pheeeew.feature.map.sighlist.SighBrowserOverlay
import com.pheeeew.feature.map.sighlist.SighModerationUiState
import com.pheeeew.feature.map.sighlist.SighReportScreen
import com.pheeeew.feature.map.sighlist.toSighListItemUiModel
import com.pheeeew.feature.map.star.StarAgePolicy
import com.pheeeew.feature.map.star.StarVisualPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun MapScreen(
    uiState: MapUiState,
    moderationUiState: SighModerationUiState,
    onSettingsClick: () -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onBoundsChanged: (SighBounds) -> Unit,
    onSighListVisibilityChange: (Boolean) -> Unit,
    onSighItemClick: (Long) -> Unit,
    onSighPinClick: (Long) -> Unit,
    onDismissSighList: () -> Unit,
    onDismissSighDetail: () -> Unit,
    onLoadNextSighPage: () -> Unit,
    onRefreshSighList: () -> Unit,
    onOpenSighActionMenu: (Long, String) -> Unit,
    onDismissSighActionMenu: () -> Unit,
    onRequestSighBlock: () -> Unit,
    onDismissSighBlock: () -> Unit,
    onConfirmSighBlock: () -> Unit,
    onRequestSighReport: () -> Unit,
    onSighReportReasonSelect: (String) -> Unit,
    onSighReportDescriptionChange: (String) -> Unit,
    onSubmitSighReport: () -> Unit,
    onDismissSighReport: () -> Unit,
    onDismissReportSuccess: () -> Unit,
    onBeginSighRegistration: () -> Unit,
    onBreathCompleted: () -> Unit,
    onSubmitMemo: (String) -> Unit,
    onSkipMemo: () -> Unit,
    onDismissMemo: () -> Unit,
    onRetrySighCreation: () -> Unit,
    onCancelFailedSighRegistration: () -> Boolean,
    onConsumeFocusRequest: (String) -> Unit,
    onEnsureLocationPermission: suspend () -> LocationPermissionStatus,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onMapError: (MapError) -> Unit,
    onMapReady: () -> Unit,
    isActive: Boolean,
    guideMode: Boolean,
    onGuideSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingFlightOrigin by remember { mutableStateOf<Offset?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var projectionSnapshot by remember { mutableStateOf(MapProjectionSnapshot.Empty) }
    var activeFlightId by remember { mutableStateOf<String?>(null) }
    var landedFlightId by remember { mutableStateOf<String?>(null) }
    var isFlightInProgress by remember { mutableStateOf(false) }
    val animationCoordinator = remember { SighAnimationCoordinator() }
    var microphoneError by remember { mutableStateOf<BreathInputError?>(null) }
    var showLocationPermissionDialog by remember { mutableStateOf(false) }
    var showLocationServicesDialog by remember { mutableStateOf(false) }
    var showMicrophonePermissionDialog by remember { mutableStateOf(false) }
    var sighPhase by remember { mutableStateOf(SighPhase.Idle) }
    var breathControlBounds by remember { mutableStateOf(Rect.Zero) }
    var cancelSignal by remember { mutableStateOf(0) }
    var breathStartSignal by remember { mutableIntStateOf(0) }
    var starAgeRevision by remember { mutableIntStateOf(0) }
    var relativeTimeRevision by remember { mutableIntStateOf(0) }
    val sighBrowser = uiState.sighBrowser
    var isSighBrowserComposed by remember { mutableStateOf(sighBrowser.isVisible) }
    val isSighSubmitting = uiState.sighRelease is SighReleaseState.Submitting
    val memoDraft = (uiState.sighRelease as? SighReleaseState.EditingMemo)?.draft
    val isMemoEditing = memoDraft != null
    val awaitingBreath = uiState.sighRelease as? SighReleaseState.AwaitingBreath
    val retryableSighError =
        (uiState.sighRelease as? SighReleaseState.Error)?.takeIf { it.canRetry }
    val cancelFailedSighRegistration = {
        if (onCancelFailedSighRegistration()) pendingFlightOrigin = null
    }
    val isSighInteractionVisible =
        sighPhase != SighPhase.Idle || isSighSubmitting || isMemoEditing || awaitingBreath != null
    val guideStep = firstSighGuideStepFor(uiState.sighRelease, sighPhase)
    val isGuidePromptVisible =
        guideMode &&
            !isMemoEditing &&
            guideStep != FirstSighGuideStep.Hidden
    val shouldShowInteractionBackdrop = isSighInteractionVisible || isGuidePromptVisible
    val currentLocation = (uiState.location.state as? LocationState.Available)?.location
    val renderedSighs =
        remember(uiState.sighs, sighBrowser.selectedSigh) {
            (listOfNotNull(sighBrowser.selectedSigh?.toPin()) + uiState.sighs)
                .distinctBy(SighPin::id)
        }
    val ensureRegistrationLocation: suspend () -> Boolean = {
        when (onEnsureLocationPermission()) {
            LocationPermissionStatus.Granted -> {
                true
            }

            LocationPermissionStatus.ServicesDisabled -> {
                showLocationServicesDialog = true
                false
            }

            LocationPermissionStatus.PermanentlyDenied -> {
                showLocationPermissionDialog = true
                false
            }

            LocationPermissionStatus.Denied -> {
                false
            }
        }
    }

    LaunchedEffect(sighBrowser.isVisible) {
        if (sighBrowser.isVisible) {
            isSighBrowserComposed = true
        } else {
            delay(SIGH_BROWSER_EXIT_DURATION_MILLIS)
            isSighBrowserComposed = false
        }
    }

    LaunchedEffect(awaitingBreath?.command?.requestId) {
        if (awaitingBreath != null) breathStartSignal += 1
    }

    LaunchedEffect(sighBrowser.isVisible) {
        if (!sighBrowser.isVisible) return@LaunchedEffect
        while (true) {
            delay(60_000L)
            relativeTimeRevision += 1
        }
    }

    val listItems =
        remember(sighBrowser.items, relativeTimeRevision) {
            val now = Clock.System.now()
            sighBrowser.items.map { it.toSighListItemUiModel(now) }
        }
    val selectedItem =
        remember(sighBrowser.selectedSigh, relativeTimeRevision) {
            sighBrowser.selectedSigh?.toSighListItemUiModel(Clock.System.now())
        }
    val selectedProjectionId = sighBrowser.selectedSigh?.let { sigh -> "selected-sigh-${sigh.id}" }
    val selectedProjectionPoint = selectedProjectionId?.let(projectionSnapshot.points::get)

    val hiddenMarkerId =
        sighBrowser.selectedSigh
            ?.id
            ?.toString()
            ?.takeIf { selectedProjectionPoint != null }
            ?: uiState.viewport.focusRequest?.id?.takeIf {
                pendingFlightOrigin != null && landedFlightId != it
            }

    LaunchedEffect(renderedSighs, isActive) {
        if (!isActive) return@LaunchedEffect

        val agePolicy = StarAgePolicy()
        while (true) {
            val nextTransitionAt =
                renderedSighs
                    .asSequence()
                    .mapNotNull { sigh -> agePolicy.nextTransitionAt(sigh.createdAt) }
                    .minOrNull()
                    ?: return@LaunchedEffect
            val delayMillis =
                (nextTransitionAt - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(1L)
            delay(delayMillis)
            starAgeRevision += 1
        }
    }

    val sighMarkers =
        remember(renderedSighs, hiddenMarkerId, starAgeRevision) {
            val now = Clock.System.now()
            renderedSighs.toSighMarkers(
                hiddenMarkerId = hiddenMarkerId,
                now = now,
            )
        }

    LaunchedEffect(uiState.viewport.focusRequest?.id, projectionSnapshot.revision) {
        val focus = uiState.viewport.focusRequest ?: return@LaunchedEffect
        pendingFlightOrigin ?: return@LaunchedEffect
        val destination = projectionSnapshot.points[focus.id] ?: return@LaunchedEffect
        if (!projectionSnapshot.cameraIdle || activeFlightId == focus.id) return@LaunchedEffect
        activeFlightId = focus.id
        isFlightInProgress = true
    }

    LaunchedEffect(uiState.sighRelease) {
        val error = uiState.sighRelease as? SighReleaseState.Error ?: return@LaunchedEffect
        if (!error.canRetry) {
            cancelFailedSighRegistration()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppTheme.colors.background)) {
        BreathMap(
            state =
                MapRenderState(
                    currentLocation = currentLocation,
                    locationState = uiState.location.state,
                    fallbackCenter = DEFAULT_MAP_POINT,
                    sighMarkers = sighMarkers,
                    focusRequest = uiState.viewport.focusRequest,
                    projectionTargets =
                        listOfNotNull(
                            sighBrowser.selectedSigh?.let { sigh ->
                                MapPoint(
                                    id = "selected-sigh-${sigh.id}",
                                    latitude = sigh.coordinate.latitude,
                                    longitude = sigh.coordinate.longitude,
                                )
                            },
                        ),
                ),
            cameraCommand = uiState.viewport.cameraCommand,
            onSighClick = { id -> id.toLongOrNull()?.let(onSighPinClick) },
            onBoundsChanged = onBoundsChanged,
            onMapError = onMapError,
            onMapRecovered = onMapReady,
            onProjectionChanged = { projectionSnapshot = it },
            modifier = Modifier.fillMaxSize(),
        )

        MapOverlay(
            onSettingsClick = onSettingsClick,
            isSighListVisible = sighBrowser.isVisible,
            onSighListVisibilityChange = onSighListVisibilityChange,
            onZoomInClick = onZoomInClick,
            onZoomOutClick = onZoomOutClick,
            onMyLocationClick = onMyLocationClick,
            errorMessage = uiState.toBannerMessage(),
            controlsEnabled =
                !guideMode &&
                    sighPhase == SighPhase.Idle &&
                    uiState.sighRelease is SighReleaseState.Idle,
        )

        if (isSighBrowserComposed) {
            SighBrowserOverlay(
                visible = sighBrowser.isVisible,
                items = listItems,
                selectedItem = selectedItem,
                selectedItemPositionPx =
                    selectedProjectionPoint?.let { point -> Offset(point.xPx, point.yPx) },
                isLoading = sighBrowser.isLoading || sighBrowser.isDetailLoading,
                isLoadingMore = sighBrowser.isLoadingMore,
                isLoadMoreError = sighBrowser.isLoadMoreError,
                refreshRevision = sighBrowser.refreshRevision,
                canLoadMore = sighBrowser.nextCursor != null,
                errorMessage = sighBrowser.errorMessage,
                moderationUiState = moderationUiState,
                onItemClick = { onSighItemClick(it.id) },
                onDismissList = onDismissSighList,
                onDismissDetail = onDismissSighDetail,
                onLoadMore = onLoadNextSighPage,
                onRefresh = onRefreshSighList,
                onOpenActionMenu = { item -> onOpenSighActionMenu(item.id, item.nickname) },
                onDismissActionMenu = onDismissSighActionMenu,
                onRequestBlock = onRequestSighBlock,
                onDismissBlock = onDismissSighBlock,
                onConfirmBlock = onConfirmSighBlock,
                onRequestReport = onRequestSighReport,
            )
        }

        if (moderationUiState.isReportVisible) {
            SighReportScreen(
                uiState = moderationUiState,
                onReasonSelect = onSighReportReasonSelect,
                onDescriptionChange = onSighReportDescriptionChange,
                onSubmit = onSubmitSighReport,
                onDismiss = onDismissSighReport,
            )
        }

        ErrorSnackbar(
            message = moderationUiState.successMessage,
            onDismiss = onDismissReportSuccess,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp),
        )

        if (shouldShowInteractionBackdrop) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .pointerInput(isGuidePromptVisible) {
                                detectTapGestures(
                                    onTap = {
                                        if (!isGuidePromptVisible) cancelSignal += 1
                                    },
                                )
                            },
                )
                if (!isGuidePromptVisible) {
                    Text(
                        text =
                            if (isSighSubmitting) {
                                "별을 만드는 중이에요"
                            } else if (isMemoEditing) {
                                ""
                            } else {
                                when (sighPhase) {
                                    SighPhase.Listening -> "후– 하고\n한숨을 내쉬어보세요"
                                    SighPhase.Quiet -> "한숨을 날려\n별을 만들어보세요"
                                    SighPhase.NeedsMore -> "한숨을 더 크게 불어주세요"
                                    SighPhase.Bursting -> ""
                                    SighPhase.Idle -> ""
                                }
                            },
                        style = AppTheme.typography.screenTitle,
                        color = AppColors.Cream100,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(BiasAlignment(0f, -0.15f)),
                    )
                } else {
                    FirstSighGuideOverlay(
                        step = guideStep,
                        controlBoundsInRoot = breathControlBounds,
                        onSkip = {
                            cancelSignal += 1
                            pendingFlightOrigin = null
                            onGuideSkip()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (isActive && !sighBrowser.isVisible) {
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().align(Alignment.BottomCenter)) {
                if (!isSighSubmitting) {
                    if (
                        uiState.sighRelease is SighReleaseState.Idle ||
                        uiState.sighRelease is SighReleaseState.AwaitingBreath
                    ) {
                        BreathControl(
                            enabled = !isFlightInProgress,
                            startSignal = breathStartSignal,
                            onIdleClick = {
                                if (uiState.sighRelease is SighReleaseState.Idle) {
                                    coroutineScope.launch {
                                        if (ensureRegistrationLocation()) onBeginSighRegistration()
                                    }
                                } else {
                                    breathStartSignal += 1
                                }
                            },
                            onExplosionFinished = { origin ->
                                pendingFlightOrigin = origin
                                onBreathCompleted()
                            },
                            onMicrophoneError = { error ->
                                if (error == BreathInputError.PermissionDenied) {
                                    showMicrophonePermissionDialog = true
                                } else {
                                    microphoneError = error
                                }
                            },
                            ensureLocationPermission = ensureRegistrationLocation,
                            onPhaseChanged = { sighPhase = it },
                            cancelSignal = cancelSignal,
                            onControlBoundsChanged = { breathControlBounds = it },
                            showIdleLabel = !guideMode,
                        )
                    }
                }
                ErrorSnackbar(
                    message = microphoneError?.toKoreanMessage(),
                    onDismiss = { microphoneError = null },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }
        }

        if (isGuidePromptVisible && guideStep == FirstSighGuideStep.SwipeUp) {
            FirstSighSwipeOverlay(
                controlBoundsInRoot = breathControlBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }

        memoDraft?.let { draft ->
            MemoEditor(
                draft = draft,
                submitting = false,
                guideMode = guideMode,
                onSubmit = onSubmitMemo,
                onSkip = onSkipMemo,
                onDismiss = {
                    pendingFlightOrigin = null
                    onDismissMemo()
                },
            )
        }

        val activeId = activeFlightId
        val origin = pendingFlightOrigin
        val destination = activeId?.let { projectionSnapshot.points[it] }
        if (
            activeId != null &&
            origin != null &&
            destination != null &&
            uiState.viewport.focusRequest?.id == activeId
        ) {
            StarFlightOverlay(
                flight = animationCoordinator.start(activeId, origin, Offset(destination.xPx, destination.yPx)),
                onLanded = { id ->
                    pendingFlightOrigin = null
                    activeFlightId = null
                    landedFlightId = id
                    isFlightInProgress = false
                    onConsumeFocusRequest(id)
                },
                onCancelled = { id ->
                    if (activeFlightId == id) {
                        pendingFlightOrigin = null
                        activeFlightId = null
                        isFlightInProgress = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showLocationPermissionDialog) {
            LocationPermissionSettingsDialog(
                onOpenSettings = {
                    showLocationPermissionDialog = false
                    onOpenLocationSettings()
                },
                onDismiss = { showLocationPermissionDialog = false },
            )
        }

        if (showLocationServicesDialog) {
            LocationServicesSettingsDialog(
                onOpenSettings = {
                    showLocationServicesDialog = false
                    onOpenLocationSettings()
                },
                onDismiss = { showLocationServicesDialog = false },
            )
        }

        if (showMicrophonePermissionDialog) {
            AppDialog(
                title = "마이크 권한 설정 안내",
                body = "한숨을 불려면 마이크 권한이 필요합니다.\n설정에서 마이크 권한을 '허용'으로 변경해주세요.",
                confirmText = "설정으로 이동",
                onConfirmClick = {
                    showMicrophonePermissionDialog = false
                    onOpenAppSettings()
                },
                onDismissRequest = { showMicrophonePermissionDialog = false },
                onDismissClick = { showMicrophonePermissionDialog = false },
                dismissText = "취소",
            )
        }

        retryableSighError?.let { error ->
            ConfirmDialog(
                title = "한숨 등록 실패",
                body = error.message,
                confirmText = "다시 시도",
                onConfirmClick = onRetrySighCreation,
                onDismissRequest = cancelFailedSighRegistration,
                onDismissClick = cancelFailedSighRegistration,
            )
        }
    }
}

private fun List<SighPin>.toSighMarkers(
    hiddenMarkerId: String?,
    now: Instant,
): List<SighMarker> =
    asSequence()
        .filterNot { it.id.toString() == hiddenMarkerId }
        .sortedBy { it.id }
        .map { sighPin ->
            SighMarker(
                id = sighPin.id.toString(),
                latitude = sighPin.coordinate.latitude,
                longitude = sighPin.coordinate.longitude,
                visual = StarVisualPolicy.visualFor(StarAgePolicy.stageOf(sighPin.createdAt, now)),
            )
        }.toList()

private val DEFAULT_MAP_POINT = MapPoint("default-location", 37.5505, 127.0373)

@Preview
@Composable
private fun MapScreenPreview() {
    AppTheme {
        MapScreen(
            uiState = MapUiState(),
            moderationUiState = SighModerationUiState(),
            onSettingsClick = {},
            onZoomInClick = {},
            onZoomOutClick = {},
            onMyLocationClick = {},
            onBoundsChanged = {},
            onSighListVisibilityChange = {},
            onSighItemClick = {},
            onSighPinClick = {},
            onDismissSighList = {},
            onDismissSighDetail = {},
            onLoadNextSighPage = {},
            onRefreshSighList = {},
            onOpenSighActionMenu = { _, _ -> },
            onDismissSighActionMenu = {},
            onRequestSighBlock = {},
            onDismissSighBlock = {},
            onConfirmSighBlock = {},
            onRequestSighReport = {},
            onSighReportReasonSelect = {},
            onSighReportDescriptionChange = {},
            onSubmitSighReport = {},
            onDismissSighReport = {},
            onDismissReportSuccess = {},
            onBeginSighRegistration = {},
            onBreathCompleted = {},
            onSubmitMemo = {},
            onSkipMemo = {},
            onDismissMemo = {},
            onRetrySighCreation = {},
            onCancelFailedSighRegistration = { true },
            onConsumeFocusRequest = {},
            onEnsureLocationPermission = { LocationPermissionStatus.Granted },
            onOpenLocationSettings = {},
            onOpenAppSettings = {},
            onMapError = {},
            onMapReady = {},
            isActive = true,
            guideMode = false,
            onGuideSkip = {},
        )
    }
}
