package com.pheeeew

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pheeeew.core.audio.LocalAudioFactories
import com.pheeeew.core.audio.rememberVoiceRecorder
import com.pheeeew.core.designsystem.component.AppAlertDialog
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.di.UiGraph
import com.pheeeew.core.navigation.DoubleBackToExitHandler
import com.pheeeew.core.navigation.GroupRootDestination
import com.pheeeew.core.navigation.MapRootDestination
import com.pheeeew.core.navigation.PressRootDestination
import com.pheeeew.core.navigation.RankingRootDestination
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.feature.component.AppBottomNavigationBar
import com.pheeeew.feature.component.AppBottomNavigationBarBottomSpacing
import com.pheeeew.feature.component.AppDestination
import com.pheeeew.feature.component.RankingBottomNavigationDestination
import com.pheeeew.feature.monitoring.product.LocalProductMonitoringVisible
import com.pheeeew.feature.screens.group.detail.GroupCopyCodeResult
import com.pheeeew.feature.screens.group.detail.GroupDetailRoute
import com.pheeeew.feature.screens.group.detail.GroupDetailViewModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.navigation.GroupDetailDestination
import com.pheeeew.feature.screens.group.navigation.GroupFeatureHost
import com.pheeeew.feature.screens.map.EmotionPinUiModel
import com.pheeeew.feature.screens.map.HighlightedPinPosition
import com.pheeeew.feature.screens.map.MapContentRenderState
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapTransientNoticeUiModel
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailLoadUiModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailOverlay
import com.pheeeew.feature.screens.map.detail.EmotionDetailViewModel
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionSheet
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.map.record.RegisteredEmotionUiModel
import com.pheeeew.feature.screens.map.record.location.RecordMapViewport
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.rememberEmotionPinSymbolImages
import com.pheeeew.feature.screens.map.rememberMapContentRenderState
import com.pheeeew.feature.screens.map.rememberRegionClusterSymbolImages
import com.pheeeew.feature.screens.map.renderer.MapCameraSnapshotUiModel
import com.pheeeew.feature.screens.map.renderer.NativeMap
import com.pheeeew.feature.screens.map.symbolImageKey
import com.pheeeew.feature.screens.onboarding.OnboardingScreen
import com.pheeeew.feature.screens.press.PressRoute
import com.pheeeew.feature.screens.ranking.press.PressRankingRoute
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingRoute
import com.pheeeew.feature.screens.report.ReportRoute
import com.pheeeew.feature.screens.settings.AppEntryGate
import com.pheeeew.feature.screens.settings.AppEntryPhase
import com.pheeeew.feature.screens.settings.AppEntryViewModel
import com.pheeeew.feature.screens.settings.AppStartupViewModel
import com.pheeeew.feature.screens.settings.SettingsScreen
import com.pheeeew.feature.screens.splash.SplashScreen
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.app_cancel
import pheeeew.shared.generated.resources.app_microphone_permission_message
import pheeeew.shared.generated.resources.app_microphone_permission_title
import pheeeew.shared.generated.resources.app_open_settings
import pheeeew.shared.generated.resources.app_update
import pheeeew.shared.generated.resources.app_update_later
import pheeeew.shared.generated.resources.app_update_required_message
import pheeeew.shared.generated.resources.app_update_required_title
import pheeeew.shared.generated.resources.app_update_retry
import pheeeew.shared.generated.resources.app_update_store_error
import pheeeew.shared.generated.resources.app_update_suggested_message
import pheeeew.shared.generated.resources.app_update_suggested_title

@Composable
fun App(
    appGraph: AppGraph,
    uiGraph: UiGraph,
    onOpenDeveloperTools: (() -> Unit)? = null,
) {
    val factory = remember(uiGraph) { uiGraph.viewModelFactory }
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides factory,
        LocalAudioFactories provides appGraph.audioFactories,
    ) {
        AppTheme { AppContent(appGraph, uiGraph, onOpenDeveloperTools) }
    }
}

@Composable
private fun AppContent(
    appGraph: AppGraph,
    uiGraph: UiGraph,
    onOpenDeveloperTools: (() -> Unit)?,
) {
    val apiDependencies = appGraph.apiDependencies
    val appVersion = appGraph.appVersion
    val permissionSettingsLauncher = appGraph.appSettingsLauncher
    val appSettingsLauncher = appGraph.appSettingsLauncher
    val onboardingStore = appGraph.onboardingStore
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val connectivityObserver = apiDependencies.connectivityObserver
    val startupViewModel: AppStartupViewModel = metroViewModel()
    val startupState by startupViewModel.state.collectAsStateWithLifecycle()
    val initialVersionCheckComplete = startupState.checked
    val versionDecision = startupState.decision
    val checkedForResume by rememberUpdatedState(initialVersionCheckComplete)
    var splashAnimationCompleted by rememberSaveable { mutableStateOf(false) }
    var suggestionDismissed by remember { mutableStateOf(false) }
    var storeOpenError by remember { mutableStateOf(false) }
    var onboardingCompleted by remember { mutableStateOf(onboardingStore.isCompleted()) }
    var mapVisible by remember { mutableStateOf(false) }
    var mapMounted by remember { mutableStateOf(false) }
    var savedMapCamera by remember { mutableStateOf<MapCameraSnapshotUiModel?>(null) }

    LaunchedEffect(startupViewModel) {
        if (!startupViewModel.state.value.checked) startupViewModel.checkVersion()
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && checkedForResume) {
                    startupViewModel.checkVersion()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requiredUpdate = versionDecision as? AppVersionDecision.UpdateRequired

    if (!splashAnimationCompleted || !initialVersionCheckComplete) {
        Box(Modifier.fillMaxSize()) {
            SplashScreen(
                animationCompleted = splashAnimationCompleted,
                onAnimationCompleted = { splashAnimationCompleted = true },
            )
        }
        return
    }

    if (requiredUpdate != null) {
        Box(Modifier.fillMaxSize()) {
            RequiredUpdateDialog(
                storeOpenError = storeOpenError,
                onOpenStore = {
                    storeOpenError = runCatching { uriHandler.openUri(requiredUpdate.storeUrl) }.isFailure
                },
                onRetry = { startupViewModel.checkVersion() },
            )
        }
        return
    }

    if (!onboardingCompleted) {
        val onboardingRecorder = rememberVoiceRecorder()
        val onboardingScope = rememberCoroutineScope()
        var microphoneDialogResult by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
        OnboardingScreen(
            monitoring = apiDependencies.client.monitoring,
            onFinished = {
                try {
                    onboardingRecorder.requestMicrophonePermission()
                    onboardingRecorder.state.first { !it.requestingPermission }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Recording can request permission again when the user needs it.
                }
                if (!onboardingRecorder.state.value.microphonePermissionGranted) {
                    val result = CompletableDeferred<Unit>()
                    microphoneDialogResult = result
                    try {
                        result.await()
                    } finally {
                        microphoneDialogResult = null
                    }
                }
                onboardingStore.complete()
                onboardingCompleted = true
            },
        )
        microphoneDialogResult?.let { result ->
            ConfirmDialog(
                title = stringResource(Res.string.app_microphone_permission_title),
                content = stringResource(Res.string.app_microphone_permission_message),
                confirmText = stringResource(Res.string.app_open_settings),
                cancelText = stringResource(Res.string.app_cancel),
                onConfirm = {
                    onboardingScope.launch {
                        try {
                            permissionSettingsLauncher.openAppSettings()
                        } finally {
                            result.complete(Unit)
                        }
                    }
                },
                onCancel = { result.complete(Unit) },
            )
        }
        return
    }

    val appEntryViewModel: AppEntryViewModel =
        metroViewModel()
    val appEntryState by appEntryViewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by apiDependencies.session.state.collectAsStateWithLifecycle()
    val sessionGeneration =
        (sessionState as? DeviceSessionResult.Ready)?.access?.generation
    val mainFeaturesReady =
        appEntryState.phase == AppEntryPhase.READY && sessionGeneration != null &&
            appEntryState.sessionGeneration == sessionGeneration

    if (mainFeaturesReady) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { apiDependencies.appSession.onForeground() }
    }

    val groupStampListRepository = uiGraph.groupStampListRepository
    val nearbyViewModel: NearbyEmotionViewModel = metroViewModel()
    val mapViewModel: MapViewModel =
        assistedMetroViewModel<MapViewModel, MapViewModel.Factory> {
            create(nearbyViewModel)
        }
    LaunchedEffect(mapViewModel, mainFeaturesReady, sessionGeneration) {
        mapViewModel.updateReadiness(mainFeaturesReady, sessionGeneration)
        if (mainFeaturesReady) mapViewModel.onMapRendererAttached()
    }
    val connectivityLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(connectivityObserver, connectivityLifecycleOwner, mapViewModel, mainFeaturesReady) {
        if (!mainFeaturesReady) return@LaunchedEffect
        connectivityLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            connectivityObserver.isConnected.collect(mapViewModel::onConnectivityChanged)
        }
    }
    val mapRecordViewModel: MapRecordViewModel = metroViewModel()
    val nearbyState by nearbyViewModel.state.collectAsStateWithLifecycle()
    var mapPageOffset by remember { mutableStateOf(0f) }
    var isMapCameraMoving by remember { mutableStateOf(false) }
    var nearbyMapControlHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(nearbyState.visible, mapViewModel) {
        if (!nearbyState.visible) mapViewModel.clearFocusedEmotion()
    }
    val mapUiModel by mapViewModel.uiModel.collectAsStateWithLifecycle()
    val recordUiModel by mapRecordViewModel.uiModel.collectAsStateWithLifecycle()
    val groupOptions by mapRecordViewModel.groupOptions.collectAsStateWithLifecycle()
    val detailViewModel: EmotionDetailViewModel = metroViewModel()
    val detailState by detailViewModel.uiModel.collectAsStateWithLifecycle()

    val mapReady = true
    LaunchedEffect(mapReady, mapVisible) {
        if (!mapReady) {
            mapMounted = false
            return@LaunchedEffect
        }
        mapMounted = true
        if (!mapVisible) {
            delay(MAP_INACTIVE_RELEASE_DELAY_MILLIS)
            if (!mapVisible) mapMounted = false
        }
    }

    var highlightedEmotion by remember { mutableStateOf<RegisteredEmotionUiModel?>(null) }
    val highlightedPinPosition = remember { mutableStateOf<HighlightedPinPosition?>(null) }
    val recordViewport = remember { mutableStateOf<RecordMapViewport?>(null) }
    var previewScale by remember { mutableStateOf(1f) }
    var mapContentActive by remember { mutableStateOf(false) }
    var pressedPinId by remember { mutableStateOf<Long?>(null) }
    val pinPressScale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()
    var pinPressJob by remember { mutableStateOf<Job?>(null) }

    val previewCoordinate = recordUiModel.selectedCoordinate ?: recordUiModel.origin
    val previewPin =
        previewCoordinate
            ?.takeIf { recordUiModel.step == RecordFlowStepUiModel.LocationSelection }
            ?.let {
                EmotionPinUiModel(
                    id = Long.MIN_VALUE,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    createdAt = "",
                    rotationDegrees = 0.0,
                    emotion = recordUiModel.selectedEmotion ?: EmotionTypeUiModel.FRUSTRATED,
                    stamp = groupOptions.firstOrNull { group -> group.id == recordUiModel.selectedGroupId }?.stamp,
                )
            }
    val pinImageModels =
        remember(mapUiModel.emotionPins, previewPin) {
            mapUiModel.emotionPins + listOfNotNull(previewPin)
        }
    val nativeMapSymbolImages = rememberEmotionPinSymbolImages(pinImageModels)
    val visibleEmotionPins =
        remember(mapUiModel.emotionPins, mapUiModel.hiddenEmotionIds) {
            mapUiModel.emotionPins.filterNot { it.id in mapUiModel.hiddenEmotionIds }
        }
    val regionImages = rememberRegionClusterSymbolImages(mapUiModel.regionClusters)
    val contentRenderState =
        rememberMapContentRenderState(
            pending =
                MapContentRenderState(
                    pins = visibleEmotionPins,
                    regions = mapUiModel.regionClusters,
                    regionLevel = mapUiModel.displayedRegionLevel,
                    pinImages = nativeMapSymbolImages.images,
                    regionImages = regionImages.images,
                    failedPinImageKeys = nativeMapSymbolImages.failedKeys,
                    failedRegionImageKeys = regionImages.failedKeys,
                    contentLoad = mapUiModel.emotionContentLoad,
                ),
            hiddenIds = mapUiModel.hiddenEmotionIds,
        )
    val nativeMapState =
        mapUiModel.copy(
            recordOrigin = recordUiModel.origin,
            recordPreviewPin = previewPin,
            recordPreviewScale = previewScale,
            emotionPinSymbolImages =
                contentRenderState.pinImages +
                    nativeMapSymbolImages.images.filter { it.key == previewPin?.symbolImageKey() },
            regionClusters = contentRenderState.regions.takeUnless { mapUiModel.isRecordLocationPicking }.orEmpty(),
            displayedRegionLevel = contentRenderState.regionLevel,
            regionClusterSymbolImages = contentRenderState.regionImages,
            highlightedEmotionId = mapUiModel.focusedEmotionId ?: highlightedEmotion?.id,
            pressedEmotionId = pressedPinId,
            pressedEmotionScale = pinPressScale.value,
            emotionContentLoad = contentRenderState.contentLoad.takeIf { mapContentActive },
            emotionPins = contentRenderState.pins,
        )

    Box(Modifier.fillMaxSize()) {
        key(mapUiModel.mapRevision) {
            NativeMap(
                state = nativeMapState,
                isVisible = mapVisible,
                isMounted = mapMounted,
                savedCamera = savedMapCamera,
                onCameraSaved = { savedMapCamera = it },
                onMemoryPressure = { if (!mapVisible) mapMounted = false },
                onMapError = mapViewModel::onMapError,
                onMapRecovered = mapViewModel::onMapRecovered,
                onRecordViewportChanged = { centerX, centerY, radius ->
                    val viewport = RecordMapViewport(centerX, centerY, radius)
                    if (recordViewport.value != viewport) recordViewport.value = viewport
                },
                onViewportChanged = mapViewModel::onViewportChanged,
                onCameraMovingChanged = { isMapCameraMoving = it },
                onEmotionPinClick = { id ->
                    highlightedEmotion = null
                    highlightedPinPosition.value = null
                    mapViewModel.clearFocusedEmotion()
                    pinPressJob?.cancel()
                    pinPressJob =
                        coroutineScope.launch {
                            pressedPinId = id
                            pinPressScale.snapTo(1f)
                            pinPressScale.animateTo(0.9f, tween(durationMillis = 70))
                            pinPressScale.animateTo(1.1f, tween(durationMillis = 110))
                            pinPressScale.animateTo(1f, tween(durationMillis = 100))
                            pressedPinId = null
                            detailViewModel.open(id, mapViewModel.exploration.viewId)
                        }
                },
                onRegionClusterClick = { id ->
                    val region = contentRenderState.regions.firstOrNull { it.id == id }
                    val level = contentRenderState.regionLevel
                    if (region != null && level != null) mapViewModel.focusOnRegionCluster(region, level)
                },
                onMapBackgroundClick = { if (nearbyState.visible) nearbyViewModel.dismiss() },
                onHighlightedPinPositionChanged = { highlightedPinPosition.value = it },
                onContentPresented = mapViewModel::contentPresented,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = mapPageOffset * size.width },
            )
        }

        val suggestedUpdate = versionDecision as? AppVersionDecision.UpdateSuggested
        if (mainFeaturesReady && suggestedUpdate != null && !suggestionDismissed) {
            AppAlertDialog(
                onDismissRequest = { suggestionDismissed = true },
                title = { Text(stringResource(Res.string.app_update_suggested_title)) },
                text = { Text(stringResource(Res.string.app_update_suggested_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (runCatching { uriHandler.openUri(suggestedUpdate.storeUrl) }.isSuccess) {
                                suggestionDismissed = true
                            }
                        },
                    ) { Text(stringResource(Res.string.app_update)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { suggestionDismissed = true },
                    ) { Text(stringResource(Res.string.app_update_later)) }
                },
            )
        }

        val audioRepository = remember(uiGraph) { uiGraph.audioRepository }
        val moderation = remember(uiGraph) { uiGraph.moderation }

        val clipboardManager = LocalClipboardManager.current
        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()

        val isRankingGroupDetailVisible =
            currentBackStackEntry?.destination?.hasRoute<GroupDetailDestination>() == true
        val selectedDestination =
            when {
                currentBackStackEntry?.destination?.hasRoute<PressRootDestination>() == true -> AppDestination.Press

                currentBackStackEntry?.destination?.hasRoute<GroupRootDestination>() == true -> AppDestination.Group

                currentBackStackEntry?.destination?.hasRoute<RankingRootDestination>() == true ||
                    isRankingGroupDetailVisible -> AppDestination.Ranking

                else -> AppDestination.Map
            }

        var isSettingsVisible by remember { mutableStateOf(false) }
        var reportTarget by remember { mutableStateOf<Triple<Long, DrawableResource, String>?>(null) }
        var moderationMessage by remember { mutableStateOf<MapTransientNoticeUiModel?>(null) }
        var nextModerationMessageId by remember { mutableStateOf(0L) }
        var isGroupDetailVisible by remember { mutableStateOf(false) }
        var isGroupCreateVisible by remember { mutableStateOf(false) }
        var refreshGroup by remember { mutableStateOf<(() -> Unit)?>(null) }
        var refreshStampRanking by remember { mutableStateOf<(() -> Unit)?>(null) }
        var refreshPressRanking by remember { mutableStateOf<(() -> Unit)?>(null) }
        var rankingDestination by remember { mutableStateOf(RankingBottomNavigationDestination.Stamp) }
        val refreshRanking =
            when (rankingDestination) {
                RankingBottomNavigationDestination.Stamp -> refreshStampRanking
                RankingBottomNavigationDestination.Press -> refreshPressRanking
            }
        var previousMainDestination by remember { mutableStateOf(AppDestination.Map) }
        LaunchedEffect(selectedDestination) {
            if (selectedDestination != AppDestination.Ranking) {
                previousMainDestination = selectedDestination
            }
        }
        val isEmotionRecordFlowActive =
            selectedDestination == AppDestination.Map &&
                (mapUiModel.isEmotionSelectorExpanded || recordUiModel.step != RecordFlowStepUiModel.Closed)

        // Register the exit fallback before navigation and screen-specific back handlers.
        DoubleBackToExitHandler()

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = MapRootDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<MapRootDestination>(
                    enterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    exitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                    popEnterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    popExitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                ) {
                    // Keep the persistent native map in step with its Compose overlay.
                    val pageOffset by transition.animateFloat(
                        transitionSpec = { tween(300) },
                        label = "nativeMapPageOffset",
                    ) { state -> if (state == EnterExitState.Visible) 0f else -1f }
                    SideEffect { mapPageOffset = pageOffset }
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapScreen(
                            viewModel = mapViewModel,
                            featuresEnabled = mainFeaturesReady,
                            recordViewModel = mapRecordViewModel,
                            highlightedEmotion = highlightedEmotion,
                            onHighlightedEmotionChanged = { highlightedEmotion = it },
                            highlightedPinPosition = highlightedPinPosition,
                            recordViewport = recordViewport,
                            onRecordPreviewScaleChanged = { previewScale = it },
                            onMapVisibilityChanged = { mapVisible = it },
                            onMapContentActiveChanged = { mapContentActive = it },
                            monitoringVisible =
                                mainFeaturesReady && !nearbyState.visible &&
                                    detailState == EmotionDetailLoadUiModel.Closed &&
                                    !isSettingsVisible && reportTarget == null,
                            feedbackVisible =
                                mainFeaturesReady && selectedDestination == AppDestination.Map &&
                                    !nearbyState.visible && detailState == EmotionDetailLoadUiModel.Closed &&
                                    !isSettingsVisible && reportTarget == null,
                            onListClick = nearbyViewModel::toggle,
                            nearbyMapControlHeight = nearbyMapControlHeight,
                            isMapCameraMoving = isMapCameraMoving,
                            onSettingClick = { isSettingsVisible = true },
                            onEmotionBubbleClick = {},
                            locationPermissionController = uiGraph.locationDependencies.permissionController,
                            appSettingsLauncher = appSettingsLauncher,
                            modifier = Modifier.fillMaxSize(),
                            message = moderationMessage,
                            onMessageDismiss = { id ->
                                if (moderationMessage?.id == id) moderationMessage = null
                            },
                            detailError = detailState as? EmotionDetailLoadUiModel.Failed,
                            onRetryDetail = detailViewModel::retry,
                            onDismissDetailError = detailViewModel::dismiss,
                        )

                        NearbyEmotionSheet(
                            nearbyViewModel,
                            onEmotionHidden = { id ->
                                mapViewModel.onEmotionHidden(id)
                                mapViewModel.refreshEmotionPins()
                            },
                            onLeaveEmotion = mapViewModel::onEmotionSelectorOpen,
                            onOpenEmotionOnMap = mapViewModel::focusOnEmotion,
                            onSheetInteraction = mapViewModel::clearFocusedEmotion,
                            focusedEmotionId = mapUiModel.focusedEmotionId,
                            onMapControlHeightChanged = { nearbyMapControlHeight = it },
                            blockUser = moderation.block,
                            onReportEmotion = { id, stamp ->
                                nearbyState.items.firstOrNull { it.id == id && !it.isMine }?.let {
                                    reportTarget = Triple(id, stamp, "list")
                                }
                            },
                            monitoringVisible = !isSettingsVisible && reportTarget == null,
                        )

                        EmotionDetailOverlay(
                            detailState,
                            detailViewModel::dismiss,
                            detailViewModel::retry,
                            audioRepository,
                            detailViewModel::toggleReaction,
                            moderation.block,
                            moderation.delete,
                            onReportClick = { id, stamp ->
                                detailViewModel.dismiss()
                                reportTarget = Triple(id, stamp, "map")
                            },
                            onBlockSucceeded = {
                                detailViewModel.dismiss()
                                mapViewModel.refreshEmotionPins()
                                moderationMessage = MapTransientNoticeUiModel(++nextModerationMessageId, "차단했어요.")
                            },
                            monitoringVisible = !isSettingsVisible && reportTarget == null,
                            onDeleteSucceeded = {
                                detailViewModel.dismiss()
                                mapViewModel.onEmotionDeleted()
                                moderationMessage = MapTransientNoticeUiModel(++nextModerationMessageId, "삭제했어요.")
                            },
                        )
                    }
                }

                composable<GroupRootDestination>(
                    enterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    exitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                    popEnterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    popExitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                ) {
                    if (!mainFeaturesReady) return@composable
                    CompositionLocalProvider(
                        LocalProductMonitoringVisible provides
                            (!isSettingsVisible && reportTarget == null),
                    ) {
                        GroupFeatureHost(
                            modifier = Modifier.fillMaxSize(),
                            onGroupDetailVisibilityChanged = { isGroupDetailVisible = it },
                            onGroupCreateVisibilityChanged = { isGroupCreateVisible = it },
                            onMembershipChanged = {
                                groupStampListRepository.invalidate()
                            },
                            onRefreshActionChanged = { refreshGroup = it },
                        )
                    }
                }

                composable<PressRootDestination>(
                    enterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    exitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                    popEnterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    popExitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                ) {
                    if (!mainFeaturesReady) return@composable
                    PressRoute(
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                composable<RankingRootDestination>(
                    enterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    exitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                    popEnterTransition = { rootTabEnterTransition(initialState.destination, targetState.destination) },
                    popExitTransition = { rootTabExitTransition(initialState.destination, targetState.destination) },
                ) { entry ->
                    if (!mainFeaturesReady) return@composable
                    val openGroupDetail: (String) -> Unit = { groupId ->
                        if (navController.currentBackStackEntry == entry) {
                            navController.navigate(GroupDetailDestination(groupId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = rankingDestination,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val direction =
                                if (targetState == RankingBottomNavigationDestination.Press) {
                                    AnimatedContentTransitionScope.SlideDirection.Left
                                } else {
                                    AnimatedContentTransitionScope.SlideDirection.Right
                                }
                            (
                                slideIntoContainer(direction, tween(300)) togetherWith
                                    slideOutOfContainer(direction, tween(300))
                            ).using(null)
                        },
                        label = "rankingTabTransition",
                    ) { destination ->
                        CompositionLocalProvider(
                            LocalProductMonitoringVisible provides
                                (destination == rankingDestination && !isSettingsVisible && reportTarget == null),
                        ) {
                            when (destination) {
                                RankingBottomNavigationDestination.Stamp -> {
                                    WeeklyRankingRoute(
                                        onGroupClick = openGroupDetail,
                                        modifier = Modifier.fillMaxSize(),
                                        onRefreshActionChanged = { refreshStampRanking = it },
                                    )
                                }

                                RankingBottomNavigationDestination.Press -> {
                                    PressRankingRoute(
                                        modifier = Modifier.fillMaxSize(),
                                        onRefreshActionChanged = { refreshPressRanking = it },
                                    )
                                }
                            }
                        }
                    }
                }

                composable<GroupDetailDestination> { entry ->
                    if (!mainFeaturesReady) return@composable
                    val destination = entry.toRoute<GroupDetailDestination>()
                    val groupId = GroupId(destination.groupId)
                    val groupDetailViewModel: GroupDetailViewModel =
                        assistedMetroViewModel<GroupDetailViewModel, GroupDetailViewModel.Factory>(
                            viewModelStoreOwner = entry,
                        ) {
                            create(groupId)
                        }

                    fun returnToRanking() {
                        groupStampListRepository.invalidate()
                        navController.popBackStack()
                    }

                    CompositionLocalProvider(
                        LocalProductMonitoringVisible provides (!isSettingsVisible && reportTarget == null),
                    ) {
                        GroupDetailRoute(
                            viewModel = groupDetailViewModel,
                            isCurrentDestination = currentBackStackEntry == entry,
                            onBack = { navController.popBackStack() },
                            onReturnHome = { returnToRanking() },
                            onJoined = groupStampListRepository::invalidate,
                            onLeft = { _, _ -> returnToRanking() },
                            onMembershipUnavailable = { _, _, _ -> returnToRanking() },
                            onCopyCode = { code, _ ->
                                clipboardManager.setText(AnnotatedString(code))
                                GroupCopyCodeResult.Copied
                            },
                            onMoodBlockClick = null,
                            onMoodReportClick = null,
                        )
                    }
                }
            }
            reportTarget?.let { (id, stamp, source) ->
                ReportRoute(
                    emotionId = id,
                    emotionStamp = stamp,
                    reportEmotion = moderation.report,
                    entrySource = source,
                    monitoring = apiDependencies.client.monitoring,
                    onBack = { reportTarget = null },
                    onReportSucceeded = {
                        reportTarget = null
                        moderationMessage = MapTransientNoticeUiModel(++nextModerationMessageId, "신고가 접수됐어요.")
                    },
                )
            }

            if (isSettingsVisible) {
                SettingsScreen(
                    monitoring = apiDependencies.client.monitoring,
                    appVersion = appVersion,
                    onBackClick = { isSettingsVisible = false },
                    permissionSettingsLauncher = permissionSettingsLauncher,
                    modifier = Modifier.fillMaxSize(),
                    onOpenDeveloperTools = onOpenDeveloperTools,
                )
            }

            if (reportTarget == null && !isSettingsVisible && !nearbyState.visible && !isEmotionRecordFlowActive &&
                !isRankingGroupDetailVisible &&
                (selectedDestination != AppDestination.Group || (!isGroupDetailVisible && !isGroupCreateVisible))
            ) {
                AppBottomNavigationBar(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AppBottomNavigationBarBottomSpacing),
                    selectedDestination = selectedDestination,
                    rankingDestination = rankingDestination,
                    onRankingBackClick = {
                        nearbyViewModel.dismiss()
                        val route =
                            when (previousMainDestination) {
                                AppDestination.Map, AppDestination.Ranking -> MapRootDestination
                                AppDestination.Press -> PressRootDestination
                                AppDestination.Group -> GroupRootDestination
                            }
                        navController.navigate(route) {
                            popUpTo<MapRootDestination> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onRankingDestinationSelected = { destination ->
                        rankingDestination = destination
                    },
                    onDestinationSelected = { destination ->
                        if (!mainFeaturesReady) return@AppBottomNavigationBar
                        nearbyViewModel.dismiss()
                        if (destination == AppDestination.Ranking) {
                            if (selectedDestination != AppDestination.Ranking) {
                                previousMainDestination = selectedDestination
                            }
                            rankingDestination = RankingBottomNavigationDestination.Stamp
                        } else {
                            previousMainDestination = destination
                        }
                        val route =
                            when (destination) {
                                AppDestination.Map -> MapRootDestination
                                AppDestination.Press -> PressRootDestination
                                AppDestination.Group -> GroupRootDestination
                                AppDestination.Ranking -> RankingRootDestination
                            }

                        if (navController.currentDestination?.hasRoute(route::class) == true) {
                            when (destination) {
                                AppDestination.Map -> mapViewModel.refreshEmotionPins()
                                AppDestination.Press -> Unit
                                AppDestination.Group -> refreshGroup?.invoke()
                                AppDestination.Ranking -> refreshRanking?.invoke()
                            }
                        } else {
                            navController.navigate(route) {
                                popUpTo<MapRootDestination> { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }
        AppEntryGate(
            state = appEntryState,
            onNicknameChanged = appEntryViewModel::onNicknameChanged,
            onSubmit = appEntryViewModel::submitNickname,
            onRetry = appEntryViewModel::retry,
        )
    }
}

@Composable
private fun RequiredUpdateDialog(
    storeOpenError: Boolean,
    onOpenStore: () -> Unit,
    onRetry: () -> Unit,
) {
    AppDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            color = Color.White,
            shape =
                RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.app_update_required_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text =
                        if (storeOpenError) {
                            stringResource(Res.string.app_update_store_error)
                        } else {
                            stringResource(Res.string.app_update_required_message)
                        },
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOpenStore) { Text(stringResource(Res.string.app_update)) }
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.app_update_retry)) }
            }
        }
    }
}

private const val MAP_INACTIVE_RELEASE_DELAY_MILLIS = 30_000L

private fun androidx.navigation.NavDestination.rootTabIndex(): Int? =
    when {
        hasRoute<MapRootDestination>() -> 0
        hasRoute<PressRootDestination>() -> 1
        hasRoute<GroupRootDestination>() -> 2
        hasRoute<RankingRootDestination>() -> 3
        else -> null
    }

private fun rootTabEnterTransition(
    from: androidx.navigation.NavDestination,
    to: androidx.navigation.NavDestination,
): EnterTransition? {
    val fromIndex = from.rootTabIndex() ?: return null
    val toIndex = to.rootTabIndex() ?: return null
    if (fromIndex == toIndex) return EnterTransition.None
    return slideInHorizontally(tween(300)) { width -> if (toIndex > fromIndex) width else -width }
}

private fun rootTabExitTransition(
    from: androidx.navigation.NavDestination,
    to: androidx.navigation.NavDestination,
): ExitTransition? {
    val fromIndex = from.rootTabIndex() ?: return null
    val toIndex = to.rootTabIndex() ?: return null
    if (fromIndex == toIndex) return ExitTransition.None
    return slideOutHorizontally(tween(300)) { width -> if (toIndex > fromIndex) -width else width }
}
