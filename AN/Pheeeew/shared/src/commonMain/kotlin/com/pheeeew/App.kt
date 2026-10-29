package com.pheeeew

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
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
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.di.UiGraph
import com.pheeeew.core.navigation.CollectionRootDestination
import com.pheeeew.core.navigation.DailyPressRankingRootDestination
import com.pheeeew.core.navigation.DoubleBackToExitHandler
import com.pheeeew.core.navigation.FirstMainPresentationEffect
import com.pheeeew.core.navigation.FlowBackHandler
import com.pheeeew.core.navigation.MapRootDestination
import com.pheeeew.core.navigation.NotificationClickRouter
import com.pheeeew.core.navigation.NotificationDestination
import com.pheeeew.core.navigation.PressRootDestination
import com.pheeeew.core.navigation.RankingRootDestination
import com.pheeeew.core.notification.InitialNotificationPermissionRequest
import com.pheeeew.core.notification.NotificationClickInbox
import com.pheeeew.core.notification.NotificationMessageInbox
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.notification.NotificationPreferencesStore
import com.pheeeew.core.update.AppUpdateController
import com.pheeeew.core.update.AppUpdateState
import com.pheeeew.data.press.race.PressRaceView
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.feature.component.AppBottomNavigationBar
import com.pheeeew.feature.component.AppBottomNavigationBarBottomSpacing
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.component.AppDestination
import com.pheeeew.feature.monitoring.product.LocalProductMonitoringVisible
import com.pheeeew.feature.notification.NotificationClickEffect
import com.pheeeew.feature.screens.collection.CollectionRoute
import com.pheeeew.feature.screens.config.DailyRankingConfigGate
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
import com.pheeeew.feature.screens.map.renderer.LocalMapRenderer
import com.pheeeew.feature.screens.map.renderer.MapCameraSnapshotUiModel
import com.pheeeew.feature.screens.map.symbolImageKey
import com.pheeeew.feature.screens.onboarding.OnboardingScreen
import com.pheeeew.feature.screens.press.PressRaceLifecycleEffect
import com.pheeeew.feature.screens.press.PressRoute
import com.pheeeew.feature.screens.ranking.press.DailyPressRankingRoute
import com.pheeeew.feature.screens.ranking.press.PressRankingRoute
import com.pheeeew.feature.screens.report.ReportRoute
import com.pheeeew.feature.screens.settings.AppEntryGate
import com.pheeeew.feature.screens.settings.AppEntryPhase
import com.pheeeew.feature.screens.settings.AppEntryUiState
import com.pheeeew.feature.screens.settings.AppEntryViewModel
import com.pheeeew.feature.screens.settings.AppStartupViewModel
import com.pheeeew.feature.screens.settings.AppUpdatePrompt
import com.pheeeew.feature.screens.settings.SettingsScreen
import com.pheeeew.feature.screens.settings.shouldShowOptionalAppUpdatePrompt
import com.pheeeew.feature.screens.splash.SplashScreen
import com.pheeeew.feature.screens.splash.rememberSplashProgress
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

internal fun shouldShowMapFeedback(
    mainFeaturesReady: Boolean,
    selectedDestination: AppDestination?,
    nearbyVisible: Boolean,
    detailState: EmotionDetailLoadUiModel,
    settingsVisible: Boolean,
    hasReportTarget: Boolean,
): Boolean =
    mainFeaturesReady && selectedDestination == AppDestination.Map &&
        !nearbyVisible &&
        (detailState == EmotionDetailLoadUiModel.Closed || detailState is EmotionDetailLoadUiModel.Failed) &&
        !settingsVisible && !hasReportTarget

internal enum class AppStartupPresentation {
    SPLASH,
    VERSION_CHECK,
    REQUIRED_UPDATE,
    ONBOARDING,
    MAIN,
}

internal fun resolveAppStartupPresentation(
    initialVersionCheckComplete: Boolean,
    splashDismissed: Boolean,
    requiredUpdate: Boolean,
    onboardingCompleted: Boolean,
): AppStartupPresentation =
    when {
        !initialVersionCheckComplete && !onboardingCompleted -> {
            if (splashDismissed) AppStartupPresentation.VERSION_CHECK else AppStartupPresentation.SPLASH
        }

        requiredUpdate -> {
            if (splashDismissed) AppStartupPresentation.REQUIRED_UPDATE else AppStartupPresentation.SPLASH
        }

        !onboardingCompleted -> {
            if (splashDismissed) AppStartupPresentation.ONBOARDING else AppStartupPresentation.SPLASH
        }

        else -> {
            AppStartupPresentation.MAIN
        }
    }

@Composable
fun App(
    appGraph: AppGraph,
    uiGraph: UiGraph,
    settingsContent: @Composable () -> Unit = {},
    notificationClicks: NotificationClickInbox? = null,
    notificationPermissionController: NotificationPermissionController? = null,
    notificationPreferencesStore: NotificationPreferencesStore? = null,
    notificationMessages: NotificationMessageInbox? = null,
    updateController: AppUpdateController? = null,
) {
    val factory = remember(uiGraph) { uiGraph.viewModelFactory }
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides factory,
        LocalAudioFactories provides appGraph.audioFactories,
    ) {
        AppTheme {
            AppContent(
                appGraph,
                uiGraph,
                settingsContent,
                notificationClicks,
                notificationPermissionController,
                notificationPreferencesStore,
                notificationMessages,
                updateController,
            )
        }
    }
}

@Composable
private fun AppContent(
    appGraph: AppGraph,
    uiGraph: UiGraph,
    settingsContent: @Composable () -> Unit,
    notificationClicks: NotificationClickInbox?,
    notificationPermissionController: NotificationPermissionController?,
    notificationPreferencesStore: NotificationPreferencesStore?,
    notificationMessages: NotificationMessageInbox?,
    updateController: AppUpdateController?,
) {
    val apiDependencies = appGraph.apiDependencies
    val appVersion = appGraph.appVersion
    val appSettingsLauncher = appGraph.appSettingsLauncher
    val onboardingStore = appGraph.onboardingStore
    // Initialize before onboarding completes, including migration of existing installs.
    val initialNotificationPermission =
        remember(onboardingStore) { InitialNotificationPermissionRequest(onboardingStore) }
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configRepository = apiDependencies.appConfig
    val appConfig by configRepository.state.collectAsStateWithLifecycle()
    LaunchedEffect(configRepository, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            configRepository.runForeground()
        }
    }
    val connectivityObserver = apiDependencies.connectivityObserver
    val startupViewModel: AppStartupViewModel = metroViewModel()
    val startupState by startupViewModel.state.collectAsStateWithLifecycle()
    val queuedClick = notificationClicks?.queuedClick?.collectAsStateWithLifecycle()?.value
    var firstMainPresented by remember { mutableStateOf(false) }
    var startupNotificationDestination by remember { mutableStateOf<NotificationDestination?>(null) }
    val queuedDestination = queuedClick?.let { NotificationClickRouter.destinationFor(it.target) }
    val startupTarget = startupNotificationDestination ?: queuedDestination.takeUnless { firstMainPresented }
    LaunchedEffect(queuedClick, firstMainPresented) {
        if (!firstMainPresented && queuedDestination != null) {
            startupNotificationDestination = queuedDestination
        }
    }
    val initialVersionCheckComplete = startupState.checked
    val versionDecision = startupState.decision
    val checkedForResume by rememberUpdatedState(initialVersionCheckComplete)
    val suggestedUpdate = versionDecision as? AppVersionDecision.UpdateSuggested
    var suggestionDismissed by rememberSaveable(suggestedUpdate?.latestVersion) { mutableStateOf(false) }
    val updateState by (
        updateController?.state
            ?: remember { MutableStateFlow(AppUpdateState.IDLE) }
    ).collectAsStateWithLifecycle()
    var installationDeferred by remember { mutableStateOf(false) }
    var installationAttempted by rememberSaveable { mutableStateOf(false) }
    var previousUpdateState by remember { mutableStateOf(updateState) }
    LaunchedEffect(updateState) {
        if (updateState == AppUpdateState.DOWNLOADED) installationDeferred = false
        if (updateState == AppUpdateState.IDLE && previousUpdateState != AppUpdateState.IDLE) {
            installationAttempted = false
        }
        previousUpdateState = updateState
    }
    var storeOpenError by remember(versionDecision) { mutableStateOf(false) }
    var onboardingCompleted by remember { mutableStateOf(onboardingStore.isCompleted()) }
    var appResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var mapVisible by remember { mutableStateOf(false) }
    var mapMounted by remember { mutableStateOf(false) }
    var mapEntryPresent by remember { mutableStateOf(false) }
    var savedMapCamera by remember { mutableStateOf<MapCameraSnapshotUiModel?>(null) }
    val splashAnimationDuration = AppStartupViewModel.MINIMUM_SPLASH_MILLIS.toFloat()
    val splashProgress =
        rememberSplashProgress(
            animationCompleted = startupState.minimumSplashElapsed,
            initialProgress = (startupViewModel.splashElapsedMillis() / splashAnimationDuration).coerceIn(0f, 1f),
            onAnimationCompleted = {},
        )

    LaunchedEffect(startupViewModel) {
        startupViewModel.startSplash()
        startupViewModel.refreshSplashDeadline()
        if (!startupViewModel.state.value.checked) startupViewModel.checkVersion()
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { owner, event ->
                appResumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (event == Lifecycle.Event.ON_RESUME) {
                    installationDeferred = false
                    startupViewModel.refreshSplashDeadline()
                    if (checkedForResume) startupViewModel.checkVersion()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requiredUpdate = versionDecision as? AppVersionDecision.UpdateRequired
    // Restore the existing identity concurrently with the public version lookup.
    val appEntryViewModel = if (onboardingCompleted) metroViewModel<AppEntryViewModel>() else null
    val appEntryState = appEntryViewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: AppEntryUiState()
    val sessionState by apiDependencies.session.state.collectAsStateWithLifecycle()
    val sessionGeneration = (sessionState as? DeviceSessionResult.Ready)?.access?.generation
    val entryReady =
        appEntryState.phase == AppEntryPhase.READY && sessionGeneration != null &&
            appEntryState.sessionGeneration == sessionGeneration
    LaunchedEffect(startupViewModel, onboardingCompleted, appEntryState.phase, entryReady) {
        startupViewModel.updateEntryPreparation(
            onboardingCompleted,
            appEntryState.phase,
            entryReady,
        )
    }
    val startupPresentation =
        resolveAppStartupPresentation(
            initialVersionCheckComplete = initialVersionCheckComplete,
            splashDismissed = startupState.splashDismissed,
            requiredUpdate = requiredUpdate != null,
            onboardingCompleted = onboardingCompleted,
        )

    if (startupPresentation == AppStartupPresentation.SPLASH) {
        Box(Modifier.fillMaxSize()) {
            SplashScreen(progress = splashProgress)
        }
        return
    }

    if (startupPresentation == AppStartupPresentation.VERSION_CHECK) {
        Box(Modifier.fillMaxSize()) {
            AppEntryGate(AppEntryUiState(), {}, {}, {})
        }
        return
    }

    if (startupPresentation == AppStartupPresentation.REQUIRED_UPDATE) {
        val update = requireNotNull(requiredUpdate)
        Box(Modifier.fillMaxSize()) {
            AppUpdatePrompt(
                required = true,
                state = updateState,
                storeOpenError = storeOpenError,
                onUpdate = {
                    if (updateState == AppUpdateState.DOWNLOADED) {
                        updateController?.complete()
                    } else if (updateController != null) {
                        updateController.request(true)
                    } else {
                        storeOpenError = runCatching { uriHandler.openUri(update.storeUrl) }.isFailure
                    }
                },
                onOpenStore = { storeOpenError = runCatching { uriHandler.openUri(update.storeUrl) }.isFailure },
                onRetry = { startupViewModel.checkVersion() },
                onLater = {},
            )
        }
        return
    }

    if (startupPresentation == AppStartupPresentation.ONBOARDING) {
        OnboardingScreen(
            monitoring = apiDependencies.client.monitoring,
            onFinished = {
                onboardingStore.complete()
                onboardingCompleted = true
            },
        )
        return
    }

    val entryViewModel = requireNotNull(appEntryViewModel)
    val mainFeaturesReady = initialVersionCheckComplete && requiredUpdate == null && entryReady
    val coldNotificationEntry = !firstMainPresented && startupTarget != null
    val shouldPrepareMapDuringSplash =
        !startupState.splashDismissed || (mainFeaturesReady && coldNotificationEntry)

    if (mainFeaturesReady) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { apiDependencies.appSession.onForeground() }
    }

    val nearbyViewModel: NearbyEmotionViewModel = metroViewModel()
    val mapViewModel: MapViewModel =
        assistedMetroViewModel<MapViewModel, MapViewModel.Factory> {
            create(nearbyViewModel)
        }
    val mapDataActive = appResumed && (mapVisible || shouldPrepareMapDuringSplash)
    val mapRendererActive = mapMounted && appResumed
    DisposableEffect(mapViewModel, mapDataActive) {
        mapViewModel.setMapRendererActive(mapDataActive)
        onDispose { mapViewModel.setMapRendererActive(false) }
    }
    LaunchedEffect(
        mapViewModel,
        mainFeaturesReady,
        sessionGeneration,
        shouldPrepareMapDuringSplash,
        notificationPermissionController,
    ) {
        mapViewModel.updateReadiness(
            ready = mainFeaturesReady,
            generation = sessionGeneration,
            startInitialLocationRequest = !shouldPrepareMapDuringSplash,
        )
        if (mainFeaturesReady && shouldPrepareMapDuringSplash) mapViewModel.prepareCurrentLocation()
        if (mainFeaturesReady && !shouldPrepareMapDuringSplash && notificationPermissionController != null) {
            mapViewModel.awaitInitialLocationRequest()
            initialNotificationPermission.requestIfNeeded(notificationPermissionController)
        }
    }
    LaunchedEffect(mapViewModel, mainFeaturesReady, sessionGeneration) {
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
    var isMapCameraMoving by remember { mutableStateOf(false) }
    var nearbyMapControlHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(nearbyState.visible, mapViewModel) {
        if (!nearbyState.visible) mapViewModel.clearFocusedEmotion()
    }
    val mapUiModel by mapViewModel.uiModel.collectAsStateWithLifecycle()
    val recordUiModel by mapRecordViewModel.uiModel.collectAsStateWithLifecycle()
    val detailViewModel: EmotionDetailViewModel = metroViewModel()
    val detailState by detailViewModel.uiModel.collectAsStateWithLifecycle()

    val firstMapFrameReady = remember(mapUiModel.mapRevision, mapMounted) { mutableStateOf(false) }
    DisposableEffect(firstMapFrameReady) {
        onDispose { startupViewModel.updateMapPreparation(false) }
    }
    LaunchedEffect(firstMapFrameReady.value, mapUiModel.mapError, mapUiModel.mapRevision) {
        startupViewModel.updateMapPreparation(firstMapFrameReady.value || mapUiModel.mapError != null)
    }
    LaunchedEffect(mapVisible, shouldPrepareMapDuringSplash) {
        if (mapVisible || shouldPrepareMapDuringSplash) mapMounted = true
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
            emotionContentLoad =
                contentRenderState.contentLoad.takeIf {
                    mapContentActive &&
                        !shouldPrepareMapDuringSplash
                },
            emotionPins = contentRenderState.pins,
        )

    Box(Modifier.fillMaxSize()) {
        val mapRevision = mapUiModel.mapRevision
        key(mapRevision) {
            LocalMapRenderer.current.Content(
                state = nativeMapState,
                isVisible = mapVisible && !shouldPrepareMapDuringSplash,
                isActive = mapRendererActive,
                isMounted = mapMounted,
                savedCamera = savedMapCamera,
                onCameraSaved = { savedMapCamera = it },
                onMemoryPressure = {
                    if (!mapEntryPresent && !shouldPrepareMapDuringSplash) mapMounted = false
                },
                onMapError = { mapViewModel.onMapError(it, mapRevision) },
                onMapRecovered = { mapViewModel.onMapRecovered(mapRevision) },
                onFirstFrameReady = { firstMapFrameReady.value = true },
                onRecordViewportChanged = { centerX, centerY, radius ->
                    val viewport = RecordMapViewport(centerX, centerY, radius)
                    if (recordViewport.value != viewport) recordViewport.value = viewport
                },
                onViewportChanged = { viewport ->
                    if (mapDataActive) mapViewModel.onViewportChanged(viewport)
                },
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
                onContentPresented = { token, ids ->
                    if (mapDataActive && mapContentActive) mapViewModel.contentPresented(token, ids)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (mainFeaturesReady && !shouldPrepareMapDuringSplash &&
            shouldShowOptionalAppUpdatePrompt(
                suggestedUpdate,
                suggestionDismissed,
                updateState,
                installationDeferred,
                installationAttempted,
            )
        ) {
            AppUpdatePrompt(
                required = false,
                state = updateState,
                storeOpenError = storeOpenError,
                onUpdate = {
                    if (updateState == AppUpdateState.DOWNLOADED) {
                        installationAttempted = true
                        updateController?.complete()
                    } else if (updateController != null) {
                        updateController.request(false)
                    } else if (suggestedUpdate != null) {
                        storeOpenError = runCatching { uriHandler.openUri(suggestedUpdate.storeUrl) }.isFailure
                        if (!storeOpenError) suggestionDismissed = true
                    }
                },
                onOpenStore = {
                    suggestedUpdate?.let { storeOpenError = runCatching { uriHandler.openUri(it.storeUrl) }.isFailure }
                },
                onRetry = { startupViewModel.checkVersion() },
                onLater = {
                    suggestionDismissed = true
                    installationDeferred = true
                    installationAttempted = false
                },
            )
        }

        val audioRepository = remember(uiGraph) { uiGraph.audioRepository }
        val moderation = remember(uiGraph) { uiGraph.moderation }

        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()

        val selectedDestination =
            when {
                currentBackStackEntry?.destination?.hasRoute<CollectionRootDestination>() == true -> {
                    AppDestination.Collection
                }

                currentBackStackEntry?.destination?.hasRoute<PressRootDestination>() == true -> {
                    AppDestination.Press
                }

                currentBackStackEntry?.destination?.hasRoute<MapRootDestination>() == true -> {
                    AppDestination.Map
                }

                else -> {
                    null
                }
            }

        var isSettingsVisible by remember { mutableStateOf(false) }
        var reportTarget by remember { mutableStateOf<Triple<Long, DrawableResource, String>?>(null) }
        var moderationMessage by remember { mutableStateOf<MapTransientNoticeUiModel?>(null) }
        var nextModerationMessageId by remember { mutableStateOf(0L) }
        val isEmotionRecordFlowActive =
            selectedDestination == AppDestination.Map &&
                (mapUiModel.isEmotionSelectorExpanded || recordUiModel.step != RecordFlowStepUiModel.Closed)

        val pressRaceView =
            when {
                currentBackStackEntry?.destination?.hasRoute<PressRootDestination>() == true -> PressRaceView.PRESS
                currentBackStackEntry?.destination?.hasRoute<RankingRootDestination>() == true -> PressRaceView.ALL
                else -> null
            }
        PressRaceLifecycleEffect(
            session = apiDependencies.appSession.pressRace,
            view = pressRaceView,
            enabled = mainFeaturesReady && !shouldPrepareMapDuringSplash && !isSettingsVisible && reportTarget == null,
        )

        var collectionNotificationDate by remember { mutableStateOf<String?>(null) }
        var collectionRefreshRevision by remember { mutableStateOf(0L) }
        var dailyRankingRefreshRevision by remember { mutableStateOf(0L) }
        val dailyRankingDate =
            currentBackStackEntry
                ?.takeIf { it.destination.hasRoute<DailyPressRankingRootDestination>() }
                ?.toRoute<DailyPressRankingRootDestination>()
                ?.date
        val foregroundMessage = notificationMessages?.pending?.collectAsStateWithLifecycle()?.value
        val messageMatchesVisibleScreen =
            foregroundMessage?.let { message ->
                !isSettingsVisible && reportTarget == null && !isEmotionRecordFlowActive &&
                    when (message.data["type"]) {
                        "COLLECTION_CLAIMABLE", "COLLECTION_CLAIM_REMINDER" -> {
                            currentBackStackEntry?.destination?.hasRoute<CollectionRootDestination>() == true
                        }

                        "RANK_DROPPED" -> {
                            dailyRankingDate != null && message.data["date"] == dailyRankingDate
                        }

                        else -> {
                            false
                        }
                    }
            } == true

        val navigateFromNotification: (NotificationDestination) -> Unit = { destination ->
            if (!firstMainPresented) startupNotificationDestination = destination
            when (destination) {
                NotificationDestination.Press -> {
                    isSettingsVisible = false
                    reportTarget = null
                    nearbyViewModel.dismiss()
                    detailViewModel.dismiss()
                    mapViewModel.dismissFilter()
                    navController.navigate(PressRootDestination) {
                        popUpTo<MapRootDestination>()
                        launchSingleTop = true
                    }
                }

                is NotificationDestination.Collection -> {
                    isSettingsVisible = false
                    reportTarget = null
                    nearbyViewModel.dismiss()
                    detailViewModel.dismiss()
                    collectionNotificationDate = destination.date
                    collectionRefreshRevision++
                    navController.navigate(CollectionRootDestination) {
                        popUpTo<MapRootDestination>()
                        launchSingleTop = true
                    }
                }

                is NotificationDestination.DailyRanking -> {
                    isSettingsVisible = false
                    reportTarget = null
                    nearbyViewModel.dismiss()
                    detailViewModel.dismiss()
                    navController.navigate(DailyPressRankingRootDestination(destination.date)) {
                        popUpTo<MapRootDestination>()
                        launchSingleTop = true
                    }
                }

                NotificationDestination.Map -> {
                    isSettingsVisible = false
                    reportTarget = null
                    nearbyViewModel.dismiss()
                    detailViewModel.dismiss()
                    mapViewModel.dismissFilter()
                    navController.navigate(MapRootDestination) {
                        popUpTo<MapRootDestination>()
                        launchSingleTop = true
                    }
                }
            }
        }

        LaunchedEffect(foregroundMessage?.id, messageMatchesVisibleScreen, mainFeaturesReady) {
            val message = foregroundMessage ?: return@LaunchedEffect
            if (!mainFeaturesReady) return@LaunchedEffect
            if (messageMatchesVisibleScreen) {
                when (message.data["type"]) {
                    "COLLECTION_CLAIMABLE", "COLLECTION_CLAIM_REMINDER" -> collectionRefreshRevision++
                    "RANK_DROPPED" -> dailyRankingRefreshRevision++
                }
            }
            notificationMessages.acknowledge(message.id)
        }

        FirstMainPresentationEffect(
            backStackEntry = currentBackStackEntry,
            ready = mainFeaturesReady && startupState.splashDismissed,
            target = startupTarget,
            alreadyPresented = firstMainPresented,
            onPresented = { firstMainPresented = true },
        )

        NotificationClickEffect(
            inbox = notificationClicks,
            ready =
                mainFeaturesReady && (!shouldPrepareMapDuringSplash || coldNotificationEntry) &&
                    currentBackStackEntry != null && !isEmotionRecordFlowActive,
            onNavigate = navigateFromNotification,
        )

        // Register the exit fallback before navigation and screen-specific back handlers.
        DoubleBackToExitHandler()

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Keep the persistent map covered while two opaque destinations crossfade.
                    // Retain transparency throughout transitions that still include the map entry.
                    .background(if (mapEntryPresent) Color.Transparent else AppColors.GroupBackground)
                    .then(
                        if (shouldPrepareMapDuringSplash) Modifier.clearAndSetSemantics {} else Modifier,
                    ),
        ) {
            NavHost(
                navController = navController,
                startDestination = MapRootDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
            ) {
                composable<MapRootDestination>(
                    enterTransition = { if (coldNotificationEntry) EnterTransition.None else null },
                    exitTransition = { if (coldNotificationEntry) ExitTransition.None else null },
                    popEnterTransition = { if (coldNotificationEntry) EnterTransition.None else null },
                    popExitTransition = { if (coldNotificationEntry) ExitTransition.None else null },
                ) {
                    DisposableEffect(Unit) {
                        mapEntryPresent = true
                        mapMounted = true
                        onDispose { mapEntryPresent = false }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapScreen(
                            viewModel = mapViewModel,
                            remoteNotice = appConfig.config.noticeMessage,
                            featuresEnabled = mainFeaturesReady && !shouldPrepareMapDuringSplash,
                            recordViewModel = mapRecordViewModel,
                            highlightedEmotion = highlightedEmotion,
                            onHighlightedEmotionChanged = { highlightedEmotion = it },
                            highlightedPinPosition = highlightedPinPosition,
                            recordViewport = recordViewport,
                            onRecordPreviewScaleChanged = { previewScale = it },
                            onMapVisibilityChanged = { mapVisible = it },
                            onMapContentActiveChanged = { mapContentActive = it },
                            monitoringVisible =
                                mainFeaturesReady && !shouldPrepareMapDuringSplash && !nearbyState.visible &&
                                    detailState == EmotionDetailLoadUiModel.Closed &&
                                    !isSettingsVisible && reportTarget == null,
                            feedbackVisible =
                                shouldShowMapFeedback(
                                    mainFeaturesReady = mainFeaturesReady && !shouldPrepareMapDuringSplash,
                                    selectedDestination = selectedDestination,
                                    nearbyVisible = nearbyState.visible,
                                    detailState = detailState,
                                    settingsVisible = isSettingsVisible,
                                    hasReportTarget = reportTarget != null,
                                ),
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

                composable<PressRootDestination>(
                    enterTransition = { if (coldNotificationEntry) EnterTransition.None else null },
                    exitTransition = { if (coldNotificationEntry) ExitTransition.None else null },
                    popEnterTransition = { if (coldNotificationEntry) EnterTransition.None else null },
                    popExitTransition = { if (coldNotificationEntry) ExitTransition.None else null },
                ) {
                    if (!mainFeaturesReady) return@composable
                    PressRoute(
                        onOpenRanking = {
                            if (navController.currentDestination?.hasRoute<PressRootDestination>() == true) {
                                navController.navigate(RankingRootDestination) { launchSingleTop = true }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                composable<CollectionRootDestination> {
                    if (!mainFeaturesReady) return@composable
                    CollectionRoute(
                        monitoring = apiDependencies.client.monitoring,
                        sessionGeneration = sessionGeneration,
                        currentNickname = appEntryState.currentNickname,
                        notificationDate = collectionNotificationDate,
                        refreshRevision = collectionRefreshRevision,
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            Spacer(Modifier.navigationBarsPadding().height(AppBottomNavigationBarOverlaySpace))
                        },
                    )
                }

                composable<RankingRootDestination> {
                    if (!mainFeaturesReady) return@composable
                    CompositionLocalProvider(
                        LocalProductMonitoringVisible provides (!isSettingsVisible && reportTarget == null),
                    ) {
                        PressRankingRoute(
                            modifier = Modifier.fillMaxSize(),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                composable<DailyPressRankingRootDestination> { entry ->
                    if (!mainFeaturesReady) return@composable
                    val destination = entry.toRoute<DailyPressRankingRootDestination>()
                    DailyRankingConfigGate(
                        state = appConfig,
                        onRetry = { coroutineScope.launch { configRepository.refresh() } },
                        onBack = {
                            if (!navController.popBackStack()) navController.navigate(MapRootDestination)
                        },
                    ) {
                        DailyPressRankingRoute(
                            date = destination.date,
                            sessionGeneration = sessionGeneration,
                            refreshRevision = dailyRankingRefreshRevision,
                            onBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize(),
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
                    permissionSettingsLauncher = appSettingsLauncher,
                    notificationPermissionController = notificationPermissionController,
                    notificationPreferencesStore = notificationPreferencesStore,
                    modifier = Modifier.fillMaxSize(),
                    settingsContent = settingsContent,
                )
            }

            if (selectedDestination != null && reportTarget == null && !isSettingsVisible &&
                !nearbyState.visible && !isEmotionRecordFlowActive
            ) {
                AppBottomNavigationBar(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AppBottomNavigationBarBottomSpacing),
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { destination ->
                        if (!mainFeaturesReady) return@AppBottomNavigationBar
                        nearbyViewModel.dismiss()
                        if (destination == AppDestination.Collection) collectionNotificationDate = null
                        val route =
                            when (destination) {
                                AppDestination.Map -> MapRootDestination
                                AppDestination.Press -> PressRootDestination
                                AppDestination.Collection -> CollectionRootDestination
                            }

                        if (navController.currentDestination?.hasRoute(route::class) == true) {
                            when (destination) {
                                AppDestination.Map -> {
                                    mapViewModel.refreshEmotionPins()
                                }

                                AppDestination.Press -> {
                                    Unit
                                }

                                AppDestination.Collection -> {
                                    collectionNotificationDate = null
                                    collectionRefreshRevision++
                                }
                            }
                        } else {
                            if (destination == AppDestination.Map) {
                                if (!navController.popBackStack<MapRootDestination>(inclusive = false)) {
                                    navController.navigate(MapRootDestination) { launchSingleTop = true }
                                }
                            } else {
                                navController.navigate(route) {
                                    popUpTo<MapRootDestination> { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                )
            }
        }
        if (!shouldPrepareMapDuringSplash && mainFeaturesReady && mapVisible && !firstMapFrameReady.value &&
            mapUiModel.mapError == null
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularLoadingIndicator()
            }
        }
        if (!shouldPrepareMapDuringSplash) {
            AppEntryGate(
                state =
                    if (!initialVersionCheckComplete ||
                        (appEntryState.phase == AppEntryPhase.READY && !entryReady)
                    ) {
                        AppEntryUiState()
                    } else {
                        appEntryState
                    },
                onNicknameChanged = entryViewModel::onNicknameChanged,
                onSubmit = entryViewModel::submitNickname,
                onRetry = entryViewModel::retry,
            )
        }
        if (shouldPrepareMapDuringSplash) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
            ) {
                SplashScreen(progress = splashProgress)
            }
        }
    }
    FlowBackHandler(enabled = shouldPrepareMapDuringSplash, onBack = {})
}
