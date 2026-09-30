package com.pheeeew

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pheeeew.core.audio.rememberVoiceRecorder
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
import com.pheeeew.core.navigation.RankingRootDestination
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.data.remote.version.AppVersionApi
import com.pheeeew.data.remote.version.toPolicy
import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.domain.model.version.evaluateAppVersion
import com.pheeeew.domain.repository.group.LastRecordedGroupRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.component.AppBottomNavigationBar
import com.pheeeew.feature.component.AppBottomNavigationBarBottomSpacing
import com.pheeeew.feature.component.AppDestination
import com.pheeeew.feature.screens.group.navigation.GroupFeatureHost
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailLoadUiModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailOverlay
import com.pheeeew.feature.screens.map.detail.EmotionDetailViewModel
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionSheet
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel
import com.pheeeew.feature.screens.map.nearby.face
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.onboarding.OnboardingScreen
import com.pheeeew.feature.screens.ranking.WeeklyRankingRoute
import com.pheeeew.feature.screens.report.ReportRoute
import com.pheeeew.feature.screens.settings.SettingsScreen
import com.pheeeew.feature.screens.splash.SplashScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun App(
    locationDependencies: LocationDependencies,
    apiDependencies: ApiDependencies,
    lastRecordedGroupRepository: LastRecordedGroupRepository,
    appVersion: String,
    appVersionApi: AppVersionApi,
    permissionSettingsLauncher: AppSettingsLauncher,
    appSettingsLauncher: AppSettingsLauncher,
    hasCompletedOnboarding: Boolean,
    onOnboardingCompleted: () -> Unit,
    connectivityObserver: ConnectivityObserver,
) {
    AppTheme {
        AppContent(
            locationDependencies = locationDependencies,
            apiDependencies = apiDependencies,
            lastRecordedGroupRepository = lastRecordedGroupRepository,
            appVersion = appVersion,
            appVersionApi = appVersionApi,
            connectivityObserver = connectivityObserver,
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
    appVersion: String,
    appVersionApi: AppVersionApi,
    connectivityObserver: ConnectivityObserver,
    permissionSettingsLauncher: AppSettingsLauncher,
    appSettingsLauncher: AppSettingsLauncher,
    hasCompletedOnboarding: Boolean,
    onOnboardingCompleted: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var versionCheckAttempt by remember { mutableStateOf(0) }
    var initialVersionCheckComplete by remember { mutableStateOf(false) }
    var splashAnimationCompleted by rememberSaveable { mutableStateOf(false) }
    var versionDecision by remember { mutableStateOf<AppVersionDecision?>(null) }
    var suggestionDismissed by remember { mutableStateOf(false) }
    var storeOpenError by remember { mutableStateOf(false) }

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

    if (!splashAnimationCompleted || !initialVersionCheckComplete) {
        SplashScreen(
            animationCompleted = splashAnimationCompleted,
            onAnimationCompleted = { splashAnimationCompleted = true },
        )
        return
    }

    val requiredUpdate = versionDecision as? AppVersionDecision.UpdateRequired
    if (requiredUpdate != null) {
        RequiredUpdateDialog(
            storeOpenError = storeOpenError,
            onOpenStore = {
                storeOpenError = runCatching { uriHandler.openUri(requiredUpdate.storeUrl) }.isFailure
            },
            onRetry = { versionCheckAttempt++ },
        )
        return
    }

    var onboardingCompleted by remember { mutableStateOf(hasCompletedOnboarding) }
    val suggestedUpdate = versionDecision as? AppVersionDecision.UpdateSuggested
    if (suggestedUpdate != null && !suggestionDismissed) {
        AlertDialog(
            onDismissRequest = { suggestionDismissed = true },
            title = { Text("새로운 버전이 나왔어요") },
            text = { Text("최신 버전으로 업데이트하면 더 나은 앱을 이용할 수 있어요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (runCatching { uriHandler.openUri(suggestedUpdate.storeUrl) }.isSuccess) {
                            suggestionDismissed = true
                        }
                    },
                ) { Text("업데이트") }
            },
            dismissButton = {
                TextButton(onClick = { suggestionDismissed = true }) { Text("나중에") }
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
                title = "마이크 권한이 필요해요",
                content = "음성을 녹음하려면 마이크 권한을 허용해 주세요.\n설정에서 권한을 켤 수 있어요.",
                confirmText = "설정으로 이동",
                cancelText = "취소",
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
            )
        }
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

    val detailRepository =
        remember(apiDependencies.client) {
            createEmotionDetailRepository(apiDependencies.client)
        }

    val detailViewModel: EmotionDetailViewModel =
        viewModel {
            EmotionDetailViewModel(detailRepository, apiDependencies.client.monitoring)
        }

    val detailState by detailViewModel.uiModel.collectAsState()
    val audioRepository = remember { createEmotionAudioRepository() }

    val moderation =
        remember(apiDependencies.client) {
            createEmotionModerationDependencies(apiDependencies.client)
        }
    val groupDependencies =
        remember(apiDependencies.client) {
            createGroupDependencies(apiDependencies.client)
        }

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val selectedDestination =
        when (currentBackStackEntry?.destination?.route) {
            MapRootDestination::class.qualifiedName -> AppDestination.Map
            GroupRootDestination::class.qualifiedName -> AppDestination.Group
            RankingRootDestination::class.qualifiedName -> AppDestination.Ranking
            else -> AppDestination.Map
        }

    var isSettingsVisible by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Triple<Long, DrawableResource, String>?>(null) }
    var moderationMessage by remember { mutableStateOf<String?>(null) }
    var isGroupDetailVisible by remember { mutableStateOf(false) }
    var isGroupCreateVisible by remember { mutableStateOf(false) }
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
            composable<MapRootDestination> {
                Box(modifier = Modifier.fillMaxSize()) {
                    MapScreen(
                        viewModel = mapViewModel,
                        recordViewModel = mapRecordViewModel,
                        onEmotionPinClick = { id -> detailViewModel.open(id, mapViewModel.exploration.viewId) },
                        monitoringVisible =
                            !nearbyState.visible && detailState == EmotionDetailLoadUiModel.Closed &&
                                !isSettingsVisible && reportTarget == null,
                        onListClick = nearbyViewModel::toggle,
                        onMapBackgroundClick = {
                            if (nearbyState.visible) nearbyViewModel.dismiss()
                        },
                        onViewportChanged = nearbyViewModel::onViewportChanged,
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
                    if (initialState.destination.route == RankingRootDestination::class.qualifiedName) {
                        slideInHorizontally(tween(300)) { -it }
                    } else {
                        null
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == RankingRootDestination::class.qualifiedName) {
                        slideOutHorizontally(tween(300)) { -it }
                    } else {
                        null
                    }
                },
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.pheeeew.feature.monitoring.product.LocalProductMonitoringVisible provides
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
                    )
                }
            }

            composable<RankingRootDestination>(
                enterTransition = {
                    if (initialState.destination.route == GroupRootDestination::class.qualifiedName) {
                        slideInHorizontally(tween(300)) { it }
                    } else {
                        null
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == GroupRootDestination::class.qualifiedName) {
                        slideOutHorizontally(tween(300)) { it }
                    } else {
                        null
                    }
                },
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.pheeeew.feature.monitoring.product.LocalProductMonitoringVisible provides
                        (!isSettingsVisible && reportTarget == null),
                ) {
                    WeeklyRankingRoute(
                        apiDependencies.client,
                        Modifier.fillMaxSize(),
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
            (selectedDestination != AppDestination.Group || (!isGroupDetailVisible && !isGroupCreateVisible))
        ) {
            AppBottomNavigationBar(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AppBottomNavigationBarBottomSpacing),
                selectedDestination = selectedDestination,
                onDestinationSelected = { destination ->
                    nearbyViewModel.dismiss()
                    val route =
                        when (destination) {
                            AppDestination.Map -> MapRootDestination
                            AppDestination.Group -> GroupRootDestination
                            AppDestination.Ranking -> RankingRootDestination
                        }

                    navController.navigate(route) {
                        popUpTo<MapRootDestination> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

@Composable
private fun RequiredUpdateDialog(
    storeOpenError: Boolean,
    onOpenStore: () -> Unit,
    onRetry: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            color = Color.White,
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("앱 업데이트가 필요해요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    text =
                        if (storeOpenError) {
                            "스토어를 열 수 없어요. 다시 시도하거나 앱 버전을 확인해 주세요."
                        } else {
                            "현재 버전은 더 이상 지원되지 않아요. 최신 버전으로 업데이트한 뒤 이용해 주세요."
                        },
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOpenStore) { Text("업데이트") }
                TextButton(onClick = onRetry) { Text("다시 확인") }
            }
        }
    }
}

private const val VERSION_CHECK_TIMEOUT_MILLIS = 10_000L
