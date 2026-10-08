package com.pheeeew

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pheeeew.core.audio.rememberVoiceRecorder
import com.pheeeew.core.designsystem.component.AppAlertDialog
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.di.ApiDependencies
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.di.createEmotionAudioRepository
import com.pheeeew.core.di.createEmotionDetailRepository
import com.pheeeew.core.di.createEmotionMapDependencies
import com.pheeeew.core.di.createEmotionModerationDependencies
import com.pheeeew.core.di.createEmotionRegistrationRepository
import com.pheeeew.core.di.emotion.createNearbyEmotionViewModel
import com.pheeeew.core.di.group.createGroupDependencies
import com.pheeeew.core.di.group.createGroupStampListRepository
import com.pheeeew.core.navigation.DoubleBackToExitHandler
import com.pheeeew.core.navigation.GroupRootDestination
import com.pheeeew.core.navigation.MapRootDestination
import com.pheeeew.core.navigation.PressRootDestination
import com.pheeeew.core.navigation.RankingRootDestination
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.data.remote.version.AppVersionApi
import com.pheeeew.data.remote.version.toPolicy
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.domain.model.version.evaluateAppVersion
import com.pheeeew.domain.repository.group.LastRecordedGroupRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.component.AppBottomNavigationBar
import com.pheeeew.feature.component.AppBottomNavigationBarBottomSpacing
import com.pheeeew.feature.component.AppDestination
import com.pheeeew.feature.component.RankingBottomNavigationDestination
import com.pheeeew.feature.component.emotion.face
import com.pheeeew.feature.monitoring.product.LocalProductMonitoringVisible
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.detail.GroupCopyCodeResult
import com.pheeeew.feature.screens.group.detail.GroupDetailRoute
import com.pheeeew.feature.screens.group.detail.GroupDetailViewModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.navigation.GroupDetailDestination
import com.pheeeew.feature.screens.group.navigation.GroupFeatureHost
import com.pheeeew.feature.screens.map.EmotionPinUiModel
import com.pheeeew.feature.screens.map.HighlightedPinPosition
import com.pheeeew.feature.screens.map.MapScreen
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
import com.pheeeew.feature.screens.map.rememberRegionClusterRenderState
import com.pheeeew.feature.screens.map.rememberRegionClusterSymbolImages
import com.pheeeew.feature.screens.map.renderer.MapCameraSnapshotUiModel
import com.pheeeew.feature.screens.map.renderer.NativeMap
import com.pheeeew.feature.screens.onboarding.OnboardingScreen
import com.pheeeew.feature.screens.press.PressRoute
import com.pheeeew.feature.screens.ranking.press.PressRankingRoute
import com.pheeeew.feature.screens.ranking.stamp.WeeklyRankingRoute
import com.pheeeew.feature.screens.report.ReportRoute
import com.pheeeew.feature.screens.settings.SettingsScreen
import com.pheeeew.feature.screens.splash.SplashScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
    locationDependencies: LocationDependencies,
    apiDependencies: ApiDependencies,
    lastRecordedGroupRepository: LastRecordedGroupRepository,
    groupCreateSessionStore: GroupCreateSessionStore,
    appVersion: String,
    appVersionApi: AppVersionApi,
    permissionSettingsLauncher: AppSettingsLauncher,
    appSettingsLauncher: AppSettingsLauncher,
    hasCompletedOnboarding: Boolean,
    onOnboardingCompleted: () -> Unit,
) {
    AppTheme {
        AppContent(
            locationDependencies = locationDependencies,
            apiDependencies = apiDependencies,
            lastRecordedGroupRepository = lastRecordedGroupRepository,
            groupCreateSessionStore = groupCreateSessionStore,
            appVersion = appVersion,
            appVersionApi = appVersionApi,
            permissionSettingsLauncher = permissionSettingsLauncher,
            appSettingsLauncher = appSettingsLauncher,
            hasCompletedOnboarding = hasCompletedOnboarding,
            onOnboardingCompleted = onOnboardingCompleted,
        )
    }
}

@Composable
private fun AppContent(
    locationDependencies: LocationDependencies,
    apiDependencies: ApiDependencies,
    lastRecordedGroupRepository: LastRecordedGroupRepository,
    groupCreateSessionStore: GroupCreateSessionStore,
    appVersion: String,
    appVersionApi: AppVersionApi,
    permissionSettingsLauncher: AppSettingsLauncher,
    appSettingsLauncher: AppSettingsLauncher,
    hasCompletedOnboarding: Boolean,
    onOnboardingCompleted: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val connectivityObserver = apiDependencies.connectivityObserver
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { apiDependencies.appSession.onForeground() }
    var versionCheckAttempt by remember { mutableStateOf(0) }
    var initialVersionCheckComplete by remember { mutableStateOf(false) }
    var splashAnimationCompleted by rememberSaveable { mutableStateOf(false) }
    var versionDecision by remember { mutableStateOf<AppVersionDecision?>(null) }
    var suggestionDismissed by remember { mutableStateOf(false) }
    var storeOpenError by remember { mutableStateOf(false) }
    var onboardingCompleted by remember { mutableStateOf(hasCompletedOnboarding) }
    var mapVisible by remember { mutableStateOf(false) }
    var mapMounted by remember { mutableStateOf(false) }
    var savedMapCamera by remember { mutableStateOf<MapCameraSnapshotUiModel?>(null) }

    LaunchedEffect(apiDependencies) {
        apiDependencies.prepareSession()
    }

    val emotionMapDependencies =
        remember(apiDependencies.client) {
            createEmotionMapDependencies(apiDependencies.client)
        }
    val mapViewModel: MapViewModel =
        viewModel {
            MapViewModel.create(
                locationDependencies,
                emotionMapDependencies.findPage,
                emotionMapDependencies.findSnapshot,
                apiDependencies.client.monitoring,
                findRegions = emotionMapDependencies.findRegions,
                findRegionSnapshot = emotionMapDependencies.findRegionSnapshot,
            )
        }
    LaunchedEffect(mapViewModel) { mapViewModel.onMapRendererAttached() }
    val connectivityLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(connectivityObserver, connectivityLifecycleOwner, mapViewModel) {
        connectivityLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            connectivityObserver.isConnected.collect(mapViewModel::onConnectivityChanged)
        }
    }
    val registrationRepository =
        remember(apiDependencies.client) { createEmotionRegistrationRepository(apiDependencies.client) }
    val groupStampListRepository =
        remember(apiDependencies.client) { createGroupStampListRepository(apiDependencies.client) }
    val mapRecordViewModel: MapRecordViewModel =
        viewModel {
            MapRecordViewModel(
                IsWithinEmotionRecordRadiusUseCase(),
                registrationRepository,
                groupStampListRepository,
                lastRecordedGroupRepository,
                apiDependencies.client.monitoring,
            )
        }
    val nearbyViewModel: NearbyEmotionViewModel =
        viewModel { createNearbyEmotionViewModel(apiDependencies.client, groupStampListRepository) }
    val nearbyState by nearbyViewModel.state.collectAsState()
    LaunchedEffect(nearbyState.visible, mapViewModel) {
        if (!nearbyState.visible) mapViewModel.clearFocusedEmotion()
    }
    val mapUiModel by mapViewModel.uiModel.collectAsState()
    val recordUiModel by mapRecordViewModel.uiModel.collectAsState()
    val groupOptions by mapRecordViewModel.groupOptions.collectAsState()
    val detailRepository = remember(apiDependencies.client) { createEmotionDetailRepository(apiDependencies.client) }
    val detailViewModel: EmotionDetailViewModel =
        viewModel { EmotionDetailViewModel(detailRepository, apiDependencies.client.monitoring) }
    val detailState by detailViewModel.uiModel.collectAsState()

    val requiredUpdate = versionDecision as? AppVersionDecision.UpdateRequired
    val mapReady =
        splashAnimationCompleted && initialVersionCheckComplete && requiredUpdate == null && onboardingCompleted
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
    val nativeMapSymbolImages = rememberEmotionPinSymbolImages(mapUiModel.emotionPins + listOfNotNull(previewPin))
    val regionImages = rememberRegionClusterSymbolImages(mapUiModel.regionClusters)
    val regionRenderState = rememberRegionClusterRenderState(mapUiModel.regionClusters, regionImages)
    val nativeMapState =
        mapUiModel.copy(
            recordOrigin = recordUiModel.origin,
            recordPreviewPin = previewPin,
            recordPreviewScale = previewScale,
            emotionPinSymbolImages = nativeMapSymbolImages,
            regionClusters = regionRenderState.regions.takeUnless { mapUiModel.isRecordLocationPicking }.orEmpty(),
            regionClusterSymbolImages = regionRenderState.images,
            highlightedEmotionId = mapUiModel.focusedEmotionId ?: highlightedEmotion?.id,
            pressedEmotionId = pressedPinId,
            pressedEmotionScale = pinPressScale.value,
            emotionContentLoad = mapUiModel.emotionContentLoad.takeIf { mapContentActive },
            emotionPins = mapUiModel.emotionPins.filterNot { it.id in mapUiModel.hiddenEmotionIds },
        )

    LaunchedEffect(appVersionApi, appVersion, versionCheckAttempt) {
        try {
            val policy = withTimeout(VERSION_CHECK_TIMEOUT_MILLIS) { appVersionApi.getPolicy().toPolicy() }
            versionDecision = evaluateAppVersion(appVersion, policy)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Fail open if the public version-policy endpoint is temporarily unavailable.
        } finally {
            initialVersionCheckComplete = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && initialVersionCheckComplete) {
                    versionCheckAttempt++
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                onViewportChanged = { viewport ->
                    mapViewModel.onViewportChanged(viewport)
                    val bounds = viewport.bounds
                    nearbyViewModel.onViewportChanged(
                        EmotionBounds(
                            bounds.minLongitude,
                            bounds.minLatitude,
                            bounds.maxLongitude,
                            bounds.maxLatitude,
                        ),
                    )
                },
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
                onRegionClusterClick = mapViewModel::focusOnRegionCluster,
                onMapBackgroundClick = { if (nearbyState.visible) nearbyViewModel.dismiss() },
                onHighlightedPinPositionChanged = { highlightedPinPosition.value = it },
                onContentPresented = mapViewModel::contentPresented,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!splashAnimationCompleted || !initialVersionCheckComplete) {
            SplashScreen(
                animationCompleted = splashAnimationCompleted,
                onAnimationCompleted = { splashAnimationCompleted = true },
            )
            return@Box
        }

        if (requiredUpdate != null) {
            RequiredUpdateDialog(
                storeOpenError = storeOpenError,
                onOpenStore = {
                    storeOpenError = runCatching { uriHandler.openUri(requiredUpdate.storeUrl) }.isFailure
                },
                onRetry = { versionCheckAttempt++ },
            )
            return@Box
        }

        val suggestedUpdate = versionDecision as? AppVersionDecision.UpdateSuggested
        if (suggestedUpdate != null && !suggestionDismissed) {
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
                    onOnboardingCompleted()
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
            return@Box
        }
        val audioRepository = remember { createEmotionAudioRepository() }

        val moderation =
            remember(apiDependencies.client) {
                createEmotionModerationDependencies(apiDependencies.client)
            }
        val groupDependencies =
            remember(apiDependencies.client, groupStampListRepository, groupCreateSessionStore) {
                createGroupDependencies(
                    apiClient = apiDependencies.client,
                    membershipChanges = groupStampListRepository.membershipChanges,
                    invalidateSharedMembership = groupStampListRepository::invalidate,
                    createSessionStore = groupCreateSessionStore,
                )
            }

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
        var moderationMessage by remember { mutableStateOf<String?>(null) }
        var isGroupDetailVisible by remember { mutableStateOf(false) }
        var isGroupCreateVisible by remember { mutableStateOf(false) }
        var refreshGroup by remember { mutableStateOf<(() -> Unit)?>(null) }
        var refreshRanking by remember { mutableStateOf<(() -> Unit)?>(null) }
        var rankingDestination by remember { mutableStateOf(RankingBottomNavigationDestination.Stamp) }
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
                    enterTransition = {
                        if (initialState.destination.hasRoute<RankingRootDestination>()) {
                            slideInHorizontally(tween(300)) { -it }
                        } else {
                            null
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.hasRoute<RankingRootDestination>()) {
                            slideOutHorizontally(tween(300)) { -it }
                        } else {
                            null
                        }
                    },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapScreen(
                            viewModel = mapViewModel,
                            recordViewModel = mapRecordViewModel,
                            highlightedEmotion = highlightedEmotion,
                            onHighlightedEmotionChanged = { highlightedEmotion = it },
                            highlightedPinPosition = highlightedPinPosition,
                            recordViewport = recordViewport,
                            onRecordPreviewScaleChanged = { previewScale = it },
                            onMapVisibilityChanged = { mapVisible = it },
                            onMapContentActiveChanged = { mapContentActive = it },
                            monitoringVisible =
                                !nearbyState.visible && detailState == EmotionDetailLoadUiModel.Closed &&
                                    !isSettingsVisible && reportTarget == null,
                            onListClick = nearbyViewModel::toggle,
                            onSettingClick = { isSettingsVisible = true },
                            onEmotionBubbleClick = {},
                            locationPermissionController = locationDependencies.permissionController,
                            appSettingsLauncher = appSettingsLauncher,
                            modifier = Modifier.fillMaxSize(),
                            message = moderationMessage,
                            onMessageDismiss = { moderationMessage = null },
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
                                moderationMessage = "차단했어요."
                            },
                            monitoringVisible = !isSettingsVisible && reportTarget == null,
                            onDeleteSucceeded = {
                                detailViewModel.dismiss()
                                mapViewModel.refreshEmotionPins()
                                moderationMessage = "삭제했어요."
                            },
                        )
                    }
                }

                composable<GroupRootDestination>(
                    enterTransition = {
                        if (initialState.destination.hasRoute<RankingRootDestination>()) {
                            slideInHorizontally(tween(300)) { -it }
                        } else {
                            null
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.hasRoute<RankingRootDestination>()) {
                            slideOutHorizontally(tween(300)) { -it }
                        } else {
                            null
                        }
                    },
                ) {
                    CompositionLocalProvider(
                        LocalProductMonitoringVisible provides
                            (!isSettingsVisible && reportTarget == null),
                    ) {
                        GroupFeatureHost(
                            dependencies = groupDependencies,
                            modifier = Modifier.fillMaxSize(),
                            onGroupDetailVisibilityChanged = { isGroupDetailVisible = it },
                            onGroupCreateVisibilityChanged = { isGroupCreateVisible = it },
                            onMembershipChanged = {
                                groupStampListRepository.invalidate()
                                nearbyViewModel.onMembershipChanged()
                            },
                            onRefreshActionChanged = { refreshGroup = it },
                        )
                    }
                }

                composable<PressRootDestination> {
                    PressRoute(
                        repository = apiDependencies.appSession.pressRepository,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                composable<RankingRootDestination>(
                    enterTransition = {
                        if (initialState.destination.hasRoute<GroupRootDestination>() ||
                            initialState.destination.hasRoute<MapRootDestination>()
                        ) {
                            slideInHorizontally(tween(300)) { it }
                        } else {
                            null
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.hasRoute<GroupRootDestination>() ||
                            targetState.destination.hasRoute<MapRootDestination>()
                        ) {
                            slideOutHorizontally(tween(300)) { it }
                        } else {
                            null
                        }
                    },
                ) { entry ->
                    val openGroupDetail: (String) -> Unit = { groupId ->
                        if (navController.currentBackStackEntry == entry) {
                            navController.navigate(GroupDetailDestination(groupId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                    CompositionLocalProvider(
                        LocalProductMonitoringVisible provides
                            (!isSettingsVisible && reportTarget == null),
                    ) {
                        if (rankingDestination == RankingBottomNavigationDestination.Stamp) {
                            WeeklyRankingRoute(
                                apiClient = apiDependencies.client,
                                onGroupClick = openGroupDetail,
                                modifier = Modifier.fillMaxSize(),
                                onRefreshActionChanged = { refreshRanking = it },
                            )
                        } else {
                            PressRankingRoute(
                                apiClient = apiDependencies.client,
                                onGroupClick = openGroupDetail,
                                modifier = Modifier.fillMaxSize(),
                                onRefreshActionChanged = { refreshRanking = it },
                            )
                        }
                    }
                }

                composable<GroupDetailDestination> { entry ->
                    val destination = entry.toRoute<GroupDetailDestination>()
                    val groupId = GroupId(destination.groupId)
                    val groupDetailViewModel: GroupDetailViewModel =
                        viewModel(viewModelStoreOwner = entry) {
                            GroupDetailViewModel(groupId = groupId, dependencies = groupDependencies.detail)
                        }

                    fun returnToRanking() {
                        groupStampListRepository.invalidate()
                        nearbyViewModel.onMembershipChanged()
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
                        moderationMessage = "신고가 접수됐어요."
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
                        if (!navController.popBackStack()) {
                            navController.navigate(MapRootDestination) { launchSingleTop = true }
                        }
                    },
                    onRankingDestinationSelected = { destination ->
                        rankingDestination = destination
                    },
                    onDestinationSelected = { destination ->
                        nearbyViewModel.dismiss()
                        if (destination == AppDestination.Ranking) {
                            rankingDestination = RankingBottomNavigationDestination.Stamp
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

private const val VERSION_CHECK_TIMEOUT_MILLIS = 10_000L
private const val MAP_INACTIVE_RELEASE_DELAY_MILLIS = 30_000L
