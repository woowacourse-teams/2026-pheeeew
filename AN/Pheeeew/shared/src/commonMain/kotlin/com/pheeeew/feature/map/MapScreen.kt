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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.audio.BreathInputError
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.feature.map.animation.SighAnimationCoordinator
import com.pheeeew.feature.map.animation.StarFlightOverlay
import com.pheeeew.feature.map.map.BreathMap
import com.pheeeew.feature.map.map.MapError
import com.pheeeew.feature.map.map.MapProjectionSnapshot
import com.pheeeew.feature.map.overlay.BreathControl
import com.pheeeew.feature.map.overlay.ErrorSnackbar
import com.pheeeew.feature.map.overlay.MapOverlay
import com.pheeeew.feature.map.overlay.MemoEditor
import com.pheeeew.feature.map.overlay.SighPhase
import com.pheeeew.feature.map.star.StarVisualPolicy

@Composable
fun MapScreen(
    uiState: MapUiState,
    onSettingsClick: () -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onBoundsChanged: (SighBounds) -> Unit,
    onBeginMemoAfterExplosion: () -> Unit,
    onSubmitMemo: (String) -> Unit,
    onSkipMemo: () -> Unit,
    onDismissMemo: () -> Unit,
    onRetrySighCreation: () -> Unit,
    onCancelFailedSighRegistration: () -> Unit,
    onConsumeFocusRequest: (String) -> Unit,
    onEnsureLocationPermission: suspend () -> LocationPermissionStatus,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onMapError: (MapError) -> Unit,
    onMapReady: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    var pendingFlightOrigin by remember { mutableStateOf<Offset?>(null) }
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
    var cancelSignal by remember { mutableStateOf(0) }
    val isSighSubmitting = uiState.sighRelease is SighReleaseState.Submitting
    val memoDraft = (uiState.sighRelease as? SighReleaseState.EditingMemo)?.draft
    val isMemoEditing = memoDraft != null
    val retryableSighError =
        (uiState.sighRelease as? SighReleaseState.Error)?.takeIf { it.canRetry }
    val isSighInteractionVisible = sighPhase != SighPhase.Idle || isSighSubmitting || isMemoEditing

    val hiddenMarkerId =
        uiState.viewport.focusRequest?.id?.takeIf {
            pendingFlightOrigin != null && landedFlightId != it
        }

    val sighMarkers =
        remember(uiState.sighs, hiddenMarkerId) {
            uiState.toSighMarkers(
                hiddenMarkerId = hiddenMarkerId,
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
            pendingFlightOrigin = null
            onCancelFailedSighRegistration()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppTheme.colors.background)) {
        BreathMap(
            state =
                MapRenderState(
                    currentLocation = (uiState.location.state as? LocationState.Available)?.location,
                    locationState = uiState.location.state,
                    fallbackCenter = DEFAULT_MAP_POINT,
                    sighMarkers = sighMarkers,
                    focusRequest = uiState.viewport.focusRequest,
                ),
            cameraCommand = uiState.viewport.cameraCommand,
            onSighClick = {},
            onBoundsChanged = onBoundsChanged,
            onMapError = onMapError,
            onMapRecovered = onMapReady,
            onProjectionChanged = { projectionSnapshot = it },
            modifier = Modifier.fillMaxSize(),
        )

        MapOverlay(
            onSettingsClick = onSettingsClick,
            onZoomInClick = onZoomInClick,
            onZoomOutClick = onZoomOutClick,
            onMyLocationClick = onMyLocationClick,
            errorMessage = uiState.toBannerMessage(),
            controlsEnabled = sighPhase == SighPhase.Idle && uiState.sighRelease is SighReleaseState.Idle,
        )

        if (isSighInteractionVisible) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { cancelSignal += 1 })
                            },
                )
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
            }
        }

        if (isActive) {
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().align(Alignment.BottomCenter)) {
                if (!isSighSubmitting) {
                    if (uiState.sighRelease is SighReleaseState.Idle) {
                        BreathControl(
                            enabled = !isFlightInProgress,
                            onExplosionFinished = { origin ->
                                pendingFlightOrigin = origin
                                onBeginMemoAfterExplosion()
                            },
                            onMicrophoneError = { error ->
                                if (error == BreathInputError.PermissionDenied) {
                                    showMicrophonePermissionDialog = true
                                } else {
                                    microphoneError = error
                                }
                            },
                            ensureLocationPermission = {
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
                            },
                            onPhaseChanged = { sighPhase = it },
                            cancelSignal = cancelSignal,
                            requestPermissionOnLaunch = false,
                        )
                    }
                }
                ErrorSnackbar(
                    message = microphoneError?.toKoreanMessage() ?: retryableSighError?.message,
                    onDismiss = { microphoneError = null },
                    onClick = retryableSighError?.let { { onRetrySighCreation() } },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }
        }

        memoDraft?.let { draft ->
            MemoEditor(
                draft = draft,
                submitting = false,
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
            AppDialog(
                title = "위치 권한 설정 안내",
                body = "한숨을 별로 만들려면 위치 권한이 필요합니다.\n설정에서 위치 권한을 '허용'으로 변경해주세요.",
                confirmText = "설정으로 이동",
                onConfirmClick = {
                    showLocationPermissionDialog = false
                    onOpenLocationSettings()
                },
                onDismissRequest = { showLocationPermissionDialog = false },
                onDismissClick = { showLocationPermissionDialog = false },
                dismissText = "취소",
            )
        }

        if (showLocationServicesDialog) {
            AppDialog(
                title = "위치 서비스 설정 안내",
                body = "현재 위치를 확인하려면 기기 설정에서 위치 서비스를 켜주세요.",
                confirmText = "설정으로 이동",
                onConfirmClick = {
                    showLocationServicesDialog = false
                    onOpenLocationSettings()
                },
                onDismissRequest = { showLocationServicesDialog = false },
                onDismissClick = { showLocationServicesDialog = false },
                dismissText = "취소",
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
    }
}

private fun MapUiState.toSighMarkers(hiddenMarkerId: String?): List<SighMarker> =
    sighs
        .asSequence()
        .filterNot { it.id.toString() == hiddenMarkerId }
        .sortedBy { it.id }
        .map { sighPin ->
            SighMarker(
                id = sighPin.id.toString(),
                latitude = sighPin.coordinate.latitude,
                longitude = sighPin.coordinate.longitude,
                visual = StarVisualPolicy.visualFor(sighPin.id.toString()),
            )
        }.toList()

private val DEFAULT_MAP_POINT = MapPoint("default-location", 37.5505, 127.0373)

@Preview
@Composable
private fun MapScreenPreview() {
    AppTheme {
        MapScreen(
            uiState = MapUiState(),
            onSettingsClick = {},
            onZoomInClick = {},
            onZoomOutClick = {},
            onMyLocationClick = {},
            onBoundsChanged = {},
            onBeginMemoAfterExplosion = {},
            onSubmitMemo = {},
            onSkipMemo = {},
            onDismissMemo = {},
            onRetrySighCreation = {},
            onCancelFailedSighRegistration = {},
            onConsumeFocusRequest = {},
            onEnsureLocationPermission = { LocationPermissionStatus.Granted },
            onOpenLocationSettings = {},
            onOpenAppSettings = {},
            onMapError = {},
            onMapReady = {},
            isActive = true,
        )
    }
}
