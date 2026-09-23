package com.pheeeew.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.legacy.core.designsystem.component.AppDialog
import com.pheeeew.legacy.core.designsystem.component.ConfirmDialog
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import com.pheeeew.legacy.core.monitoring.Monitoring
import com.pheeeew.legacy.core.navigation.DoubleBackToExitHandler
import com.pheeeew.legacy.core.navigation.PredictiveBackContent
import com.pheeeew.legacy.core.navigation.Screen
import com.pheeeew.legacy.core.network.ConnectivityObserver
import com.pheeeew.legacy.data.remote.version.AppVersionApi
import com.pheeeew.legacy.data.remote.version.toPolicy
import com.pheeeew.legacy.di.LocationDependencies
import com.pheeeew.legacy.domain.model.version.AppVersionDecision
import com.pheeeew.legacy.domain.model.version.evaluateAppVersion
import com.pheeeew.legacy.domain.repository.SighRepository
import com.pheeeew.legacy.domain.usecase.BlockUserUseCase
import com.pheeeew.legacy.domain.usecase.CreateSighUseCase
import com.pheeeew.legacy.domain.usecase.EnsureDeviceRegisteredUseCase
import com.pheeeew.legacy.domain.usecase.ReportSighUseCase
import com.pheeeew.legacy.feature.map.MapPerformanceLogger
import com.pheeeew.legacy.feature.map.MapRoute
import com.pheeeew.legacy.feature.map.MapViewModel
import com.pheeeew.legacy.feature.map.sighlist.SighModerationViewModel
import com.pheeeew.legacy.feature.onboarding.OnboardingScreen
import com.pheeeew.legacy.feature.setting.SettingsScreen
import com.pheeeew.legacy.feature.setting.legal.LegalDocument
import com.pheeeew.legacy.feature.setting.legal.LegalDocumentRoute
import com.pheeeew.legacy.feature.splash.SplashScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun LegacyApp(
    appVersion: String,
    appVersionApi: AppVersionApi,
    connectivityObserver: ConnectivityObserver,
    hasCompletedOnboarding: Boolean,
    hasCompletedFirstSighGuide: Boolean,
    onOnboardingCompleted: () -> Unit,
    onFirstSighGuideCompleted: () -> Unit,
    locationDependencies: LocationDependencies?,
    sighRepository: com.pheeeew.legacy.domain.repository.SighRepository,
    createSigh: com.pheeeew.legacy.domain.usecase.CreateSighUseCase,
    blockUser: com.pheeeew.legacy.domain.usecase.BlockUserUseCase,
    reportSigh: com.pheeeew.legacy.domain.usecase.ReportSighUseCase,
    mapPerformanceLogger: MapPerformanceLogger,
    ensureDeviceRegistered: com.pheeeew.legacy.domain.usecase.EnsureDeviceRegisteredUseCase? = null,
    monitoring: Monitoring? = null,
) {
    AppTheme {
        val coroutineScope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var screen by remember { mutableStateOf(Screen.Splash) }
        LaunchedEffect(screen) { monitoring?.setScreen(screen.name.lowercase()) }
        var splashFinished by remember { mutableStateOf(false) }
        var versionCheckAttempt by remember { mutableStateOf(0) }
        var versionGate by remember { mutableStateOf<AppVersionGate>(AppVersionGate.Checking) }
        var appMounted by remember { mutableStateOf(false) }
        var suggestionDismissed by remember { mutableStateOf(false) }
        var storeOpenError by remember { mutableStateOf<String?>(null) }
        var isConnected by remember { mutableStateOf(false) }
        val versionDecision = (versionGate as? AppVersionGate.Passed)?.decision
        val updateRequired = versionDecision is AppVersionDecision.UpdateRequired
        val latestVersionGate = rememberUpdatedState(versionGate)
        val canUseApp = appMounted && !updateRequired
        var firstSighGuideActive by remember { mutableStateOf(!hasCompletedFirstSighGuide) }

        LaunchedEffect(appVersionApi, appVersion, versionCheckAttempt) {
            val previousDecision = (versionGate as? AppVersionGate.Passed)?.decision
            if (previousDecision !is AppVersionDecision.UpdateRequired) versionGate = AppVersionGate.Checking
            try {
                val policy =
                    withTimeoutOrNull(VERSION_CHECK_TIMEOUT_MILLIS) {
                        appVersionApi.getPolicy().toPolicy()
                    } ?: error("앱 버전 정책 조회 시간이 초과되었습니다.")
                val decision = evaluateAppVersion(appVersion, policy)
                versionGate = AppVersionGate.Passed(decision)
                if (decision !is AppVersionDecision.UpdateRequired) appMounted = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (previousDecision is AppVersionDecision.UpdateRequired) {
                    versionGate = AppVersionGate.Passed(previousDecision)
                } else {
                    versionGate = AppVersionGate.Failed
                    appMounted = true
                }
            }
        }

        LaunchedEffect(connectivityObserver, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectivityObserver.isConnected.collect { connected ->
                    isConnected = connected
                    val gate = latestVersionGate.value
                    val shouldRecheck =
                        gate == AppVersionGate.Failed ||
                            (gate as? AppVersionGate.Passed)?.decision is AppVersionDecision.UpdateRequired
                    if (connected && shouldRecheck) versionCheckAttempt++
                }
            }
        }

        LaunchedEffect(versionGate, isConnected, versionCheckAttempt, lifecycleOwner) {
            if (isConnected && versionGate == AppVersionGate.Failed) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    delay(VERSION_RETRY_INTERVAL_MILLIS)
                    versionCheckAttempt++
                }
            }
        }

        LaunchedEffect(canUseApp, splashFinished) {
            if (canUseApp && splashFinished && screen == Screen.Splash) {
                screen = if (hasCompletedOnboarding) Screen.Map else Screen.Onboarding
            }
        }

        val completeFirstSighGuide = {
            if (firstSighGuideActive) {
                firstSighGuideActive = false
                onFirstSighGuideCompleted()
            }
        }
        val mapViewModel: MapViewModel =
            viewModel {
                MapViewModel(sighRepository, createSigh, locationDependencies, mapPerformanceLogger, monitoring)
            }
        val sighModerationViewModel: SighModerationViewModel =
            viewModel {
                SighModerationViewModel(
                    blockUser = blockUser,
                    reportSigh = reportSigh,
                    onBlockSucceeded = { target ->
                        mapViewModel.removeSigh(target)
                        mapViewModel.dismissSighDetail()
                    },
                    onReportSucceeded = mapViewModel::dismissSighDetail,
                )
            }
        val mapReadiness = remember { MutableStateFlow(false) }
        var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }

        @Suppress("UNUSED_VARIABLE")
        var requestPermissionsAfterOnboarding by remember { mutableStateOf(false) }
        LaunchedEffect(ensureDeviceRegistered, canUseApp) {
            // Device registration is an internal best-effort warm-up. A failure must not
            // block app entry; authenticated requests retry it when they need a token.
            if (canUseApp) ensureDeviceRegistered?.invoke()
        }

        // 오버레이 화면들이 뒤에 깔린 지도로 터치가 새어나가지 않도록 막습니다.
        val overlayModifier =
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

        Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            // Map은 항상 조립된 상태로 유지해, 화면 전환 시 지도 뷰가 매번 새로 생성되며
            // 생기는 깜박임을 막습니다. Splash/Settings/LegalDocument는 그 위에 오버레이로 뜹니다.
            if (appMounted) {
                MapRoute(
                    onSettingsClick = { screen = Screen.Settings },
                    onMapReady = { mapReadiness.value = true },
                    isActive = screen == Screen.Map && !updateRequired,
                    guideMode = firstSighGuideActive,
                    onGuideSkip = completeFirstSighGuide,
                    onSighRegistrationSucceeded = { completeFirstSighGuide() },
                    viewModel = mapViewModel,
                    moderationViewModel = sighModerationViewModel,
                )
            }

            if (screen == Screen.Map && !firstSighGuideActive && !updateRequired) {
                DoubleBackToExitHandler()
            }

            // Settings는 LegalDocument 아래에도 계속 조립된 상태로 유지해,
            // LegalDocument에서 뒤로가기 제스처로 슬라이드할 때 그 아래로 Settings가 드러나도록 합니다.
            if (screen == Screen.Settings || screen == Screen.LegalDocument) {
                PredictiveBackContent(
                    onBack = { screen = Screen.Map },
                    content = {
                        SettingsScreen(
                            onBackClick = { screen = Screen.Map },
                            onPermissionClick = {
                                locationDependencies?.let { dependencies ->
                                    coroutineScope.launch {
                                        dependencies.permissionSettingsLauncher.openAppSettings()
                                    }
                                }
                            },
                            onOpenSourceLicenseClick = {
                                selectedLegalDocument = LegalDocument.OpenSourceLicenses
                                screen = Screen.LegalDocument
                            },
                            onPrivacyPolicyClick = {
                                selectedLegalDocument = LegalDocument.PrivacyPolicy
                                screen = Screen.LegalDocument
                            },
                            appVersion = appVersion,
                            contactMail = "contact@pheeeew.com",
                        )
                    },
                    modifier = overlayModifier,
                )
            }

            if (screen == Screen.LegalDocument) {
                selectedLegalDocument?.let { document ->
                    PredictiveBackContent(
                        onBack = { screen = Screen.Settings },
                        content = {
                            LegalDocumentRoute(
                                document = document,
                                onBack = { screen = Screen.Settings },
                            )
                        },
                        modifier = overlayModifier,
                    )
                }
            }

            if (screen == Screen.Onboarding) {
                OnboardingScreen(
                    onRequestLocationPermission = {
                        mapViewModel.requestLocationPermission()
                    },
                    onOpenLocationSettings = mapViewModel::openLocationSettings,
                    onOpenAppSettings = mapViewModel::openAppSettings,
                    onFinished = {
                        onOnboardingCompleted()
                        firstSighGuideActive = true
                        screen = Screen.Map
                    },
                    modifier = overlayModifier,
                )
            }

            if (screen == Screen.Splash) {
                SplashScreen(
                    isReady = mapReadiness,
                    onFinished = { splashFinished = true },
                    modifier = overlayModifier,
                )
            }

            if (versionDecision is AppVersionDecision.UpdateRequired) {
                AppDialog(
                    title = "앱 업데이트가 필요해요",
                    body =
                        "현재 버전은 더 이상 지원되지 않아요. 최신 버전으로 업데이트한 뒤 이용해 주세요." +
                            (storeOpenError?.let { "\n$it" } ?: ""),
                    confirmText = "업데이트",
                    onConfirmClick = {
                        storeOpenError =
                            runCatching { uriHandler.openUri(versionDecision.storeUrl) }
                                .exceptionOrNull()
                                ?.let { "스토어를 열 수 없어요. 잠시 후 다시 시도해 주세요." }
                    },
                    onDismissRequest = {},
                    onDismissClick = {
                        storeOpenError = null
                        versionCheckAttempt++
                    },
                    dismissText = "다시 확인",
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                )
            }

            if (screen != Screen.Splash && versionDecision is AppVersionDecision.UpdateSuggested &&
                !suggestionDismissed
            ) {
                ConfirmDialog(
                    title = "새로운 버전이 나왔어요",
                    body = "최신 버전으로 업데이트하면 더 나은 앱을 이용할 수 있어요.",
                    confirmText = "업데이트",
                    onConfirmClick = {
                        if (runCatching { uriHandler.openUri(versionDecision.storeUrl) }.isSuccess) {
                            suggestionDismissed = true
                        }
                    },
                    onDismissRequest = { suggestionDismissed = true },
                    onDismissClick = { suggestionDismissed = true },
                    dismissText = "나중에",
                )
            }
        }
    }
}

private sealed interface AppVersionGate {
    data object Checking : AppVersionGate

    data object Failed : AppVersionGate

    data class Passed(
        val decision: AppVersionDecision,
    ) : AppVersionGate
}

private const val VERSION_CHECK_TIMEOUT_MILLIS = 10_000L
private const val VERSION_RETRY_INTERVAL_MILLIS = 30_000L
