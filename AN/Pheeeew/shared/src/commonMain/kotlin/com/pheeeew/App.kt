package com.pheeeew

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.navigation.DoubleBackToExitHandler
import com.pheeeew.core.navigation.PredictiveBackContent
import com.pheeeew.core.navigation.Screen
import com.pheeeew.di.LocationDependencies
import com.pheeeew.domain.exception.device.DeviceRegistrationException
import com.pheeeew.domain.model.device.DeviceRegistrationError
import com.pheeeew.domain.model.device.DeviceRegistrationState
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.domain.usecase.CreateSighUseCase
import com.pheeeew.domain.usecase.EnsureDeviceRegisteredUseCase
import com.pheeeew.feature.map.MapPerformanceLogger
import com.pheeeew.feature.map.MapRoute
import com.pheeeew.feature.map.MapViewModel
import com.pheeeew.feature.map.sighlist.SighModerationViewModel
import com.pheeeew.feature.onboarding.OnboardingScreen
import com.pheeeew.feature.setting.SettingsScreen
import com.pheeeew.feature.setting.legal.LegalDocument
import com.pheeeew.feature.setting.legal.LegalDocumentRoute
import com.pheeeew.feature.splash.SplashScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun App(
    appVersion: String,
    hasCompletedOnboarding: Boolean,
    onOnboardingCompleted: () -> Unit,
    locationDependencies: LocationDependencies?,
    sighRepository: SighRepository,
    createSigh: CreateSighUseCase,
    mapPerformanceLogger: MapPerformanceLogger,
    ensureDeviceRegistered: EnsureDeviceRegisteredUseCase? = null,
) {
    AppTheme {
        val coroutineScope = rememberCoroutineScope()
        var screen by remember { mutableStateOf(Screen.Splash) }
        val mapViewModel: MapViewModel =
            viewModel {
                MapViewModel(sighRepository, createSigh, locationDependencies, mapPerformanceLogger)
            }
        val sighModerationViewModel: SighModerationViewModel =
            viewModel { SighModerationViewModel() }
        val mapReadiness = remember { MutableStateFlow(false) }
        var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }
        var requestPermissionsAfterOnboarding by remember { mutableStateOf(false) }
        var registrationState by remember { mutableStateOf<DeviceRegistrationState?>(null) }

        fun registerDevice() {
            ensureDeviceRegistered ?: return
            coroutineScope.launch {
                registrationState = DeviceRegistrationState.Registering
                val result = ensureDeviceRegistered.invoke()
                registrationState =
                    result.fold(
                        onSuccess = { session ->
                            DeviceRegistrationState.Registered(session.accessTokenExpiresAtEpochSeconds)
                        },
                        onFailure = { error ->
                            DeviceRegistrationState.Failed(error.toDeviceRegistrationError())
                        },
                    )
            }
        }

        LaunchedEffect(ensureDeviceRegistered) {
            registerDevice()
        }

        // 오버레이 화면들이 뒤에 깔린 지도로 터치가 새어나가지 않도록 막습니다.
        val overlayModifier =
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

        Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            // Map은 항상 조립된 상태로 유지해, 화면 전환 시 지도 뷰가 매번 새로 생성되며
            // 생기는 깜박임을 막습니다. Splash/Settings/LegalDocument는 그 위에 오버레이로 뜹니다.
            MapRoute(
                onSettingsClick = { screen = Screen.Settings },
                onMapReady = { mapReadiness.value = true },
                isActive = screen == Screen.Map,
                requestPermissionsAfterOnboarding = requestPermissionsAfterOnboarding,
                viewModel = mapViewModel,
                moderationViewModel = sighModerationViewModel,
            )

            if (screen == Screen.Map) {
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
                    onFinished = {
                        onOnboardingCompleted()
                        requestPermissionsAfterOnboarding = true
                        screen = Screen.Map
                    },
                    modifier = overlayModifier,
                )
            }

            if (screen == Screen.Splash) {
                SplashScreen(
                    isReady = mapReadiness,
                    onFinished = {
                        screen = if (hasCompletedOnboarding) Screen.Map else Screen.Onboarding
                    },
                    modifier = overlayModifier,
                )
            }

            if (registrationState is DeviceRegistrationState.Failed) {
                val error = (registrationState as DeviceRegistrationState.Failed).error
                AppDialog(
                    title = "기기 등록 실패",
                    body = error.message(),
                    confirmText = "다시 시도",
                    onConfirmClick = ::registerDevice,
                    onDismissRequest = {},
                    onDismissClick = {},
                )
            }
        }
    }
}

private fun Throwable.toDeviceRegistrationError(): DeviceRegistrationError =
    when (this) {
        is DeviceRegistrationException.InvalidRefreshToken -> DeviceRegistrationError.InvalidRefreshToken
        is DeviceRegistrationException.DeviceNotFound -> DeviceRegistrationError.DeviceNotFound
        is DeviceRegistrationException.Network -> DeviceRegistrationError.Network
        is DeviceRegistrationException.InvalidChallenge -> DeviceRegistrationError.InvalidChallenge
        is DeviceRegistrationException.AttestationRejected -> DeviceRegistrationError.AttestationRejected
        is DeviceRegistrationException.RetryableServer -> DeviceRegistrationError.RetryableServer(retryAfterSeconds)
        is DeviceRegistrationException.Server -> DeviceRegistrationError.Server
        else -> DeviceRegistrationError.Unknown
    }

private fun DeviceRegistrationError.message(): String =
    when (this) {
        DeviceRegistrationError.Network -> {
            "네트워크 연결을 확인해주세요."
        }

        DeviceRegistrationError.InvalidRefreshToken,
        DeviceRegistrationError.DeviceNotFound,
        -> {
            "기기 인증 정보가 만료되어 다시 등록해야 합니다."
        }

        DeviceRegistrationError.InvalidChallenge -> {
            "인증 요청이 만료되었습니다. 다시 시도해주세요."
        }

        DeviceRegistrationError.AttestationRejected -> {
            "기기 무결성 확인에 실패했습니다."
        }

        DeviceRegistrationError.Server,
        DeviceRegistrationError.Unknown,
        -> {
            "잠시 후 다시 시도해주세요."
        }

        is DeviceRegistrationError.RetryableServer -> {
            val seconds = retryAfterSeconds
            if (seconds != null) "${seconds}초 후 다시 시도해주세요." else "잠시 후 다시 시도해주세요."
        }
    }
