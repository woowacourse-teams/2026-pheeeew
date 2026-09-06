package com.pheeeew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.navigation.DoubleBackToExitHandler
import com.pheeeew.core.navigation.PredictiveBackContent
import com.pheeeew.core.navigation.Screen
import com.pheeeew.di.LocationDependencies
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.feature.map.MapPerformanceLogger
import com.pheeeew.feature.map.MapRoute
import com.pheeeew.feature.map.MapViewModel
import com.pheeeew.feature.setting.SettingsScreen
import com.pheeeew.feature.setting.legal.LegalDocument
import com.pheeeew.feature.setting.legal.LegalDocumentRoute
import com.pheeeew.feature.splash.SplashScreen
import kotlinx.coroutines.launch

@Composable
fun App(
    appVersion: String,
    locationDependencies: LocationDependencies?,
    sighRepository: SighRepository,
    mapPerformanceLogger: MapPerformanceLogger = {},
) {
    AppTheme {
        val coroutineScope = rememberCoroutineScope()
        var screen by remember { mutableStateOf(Screen.Splash) }
        val mapViewModel: MapViewModel =
            viewModel {
                MapViewModel(sighRepository, locationDependencies, mapPerformanceLogger)
            }
        var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }

        // 오버레이 화면들이 뒤에 깔린 지도로 터치가 새어나가지 않도록 막습니다.
        val overlayModifier =
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

        Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            // Map은 항상 조립된 상태로 유지해, 화면 전환 시 지도 뷰가 매번 새로 생성되며
            // 생기는 깜박임을 막습니다. Splash/Settings/LegalDocument는 그 위에 오버레이로 뜹니다.
            MapRoute(
                onSettingsClick = { screen = Screen.Settings },
                isActive = screen == Screen.Map,
                viewModel = mapViewModel,
            )

            if (screen == Screen.Map) {
                DoubleBackToExitHandler()
            }

            // Settings는 LegalDocument 아래에도 계속 조립된 상태로 유지해,
            // LegalDocument에서 뒤로가기 제스처로 슬라이드할 때 그 아래로 Settings가 드러나도록 합니다.
            if (screen == Screen.Settings || screen == Screen.LegalDocument) {
                PredictiveBackContent(onBack = { screen = Screen.Map }, modifier = overlayModifier) {
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
                }
            }

            if (screen == Screen.LegalDocument) {
                selectedLegalDocument?.let { document ->
                    PredictiveBackContent(onBack = { screen = Screen.Settings }, modifier = overlayModifier) {
                        LegalDocumentRoute(
                            document = document,
                            onBack = { screen = Screen.Settings },
                        )
                    }
                }
            }

            if (screen == Screen.Splash) {
                SplashScreen(
                    isReady = mapViewModel.isReady,
                    onFinished = { screen = Screen.Map },
                    modifier = overlayModifier,
                )
            }
        }
    }
}
