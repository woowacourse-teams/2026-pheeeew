package com.pheeeew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.di.ApiDependencies
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.di.createEmotionAudioRepository
import com.pheeeew.core.di.createEmotionDetailRepository
import com.pheeeew.core.di.createEmotionMapDependencies
import com.pheeeew.core.di.createEmotionRegistrationRepository
import com.pheeeew.core.permission.AppSettingsLauncher
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailOverlay
import com.pheeeew.feature.screens.map.detail.EmotionDetailViewModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.onboarding.OnboardingScreen
import com.pheeeew.feature.screens.settings.SettingsScreen
import com.pheeeew.legacy.core.permission.LocationPermissionSettingsLauncher

@Composable
fun App(
    locationDependencies: LocationDependencies,
    apiDependencies: ApiDependencies,
    appVersion: String,
    permissionSettingsLauncher: LocationPermissionSettingsLauncher,
    appSettingsLauncher: AppSettingsLauncher,
    hasCompletedOnboarding: Boolean,
    onOnboardingCompleted: () -> Unit,
) {
    var onboardingCompleted by remember { mutableStateOf(hasCompletedOnboarding) }
    if (!onboardingCompleted) {
        OnboardingScreen(
            onFinished = {
                onOnboardingCompleted()
                onboardingCompleted = true
            },
        )
        return
    }

    LaunchedEffect(apiDependencies) { apiDependencies.prepareSession() }
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
            )
        }
    val registrationRepository =
        remember(apiDependencies.client) { createEmotionRegistrationRepository(apiDependencies.client) }
    val mapRecordViewModel: MapRecordViewModel =
        viewModel {
            MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase(), registrationRepository)
        }
    val detailRepository = remember(apiDependencies.client) { createEmotionDetailRepository(apiDependencies.client) }
    val detailViewModel: EmotionDetailViewModel =
        viewModel { EmotionDetailViewModel(detailRepository) }
    val detailState by detailViewModel.uiModel.collectAsState()
    val audioRepository = remember { createEmotionAudioRepository() }
    var isSettingsVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            viewModel = mapViewModel,
            recordViewModel = mapRecordViewModel,
            onEmotionPinClick = detailViewModel::open,
            onListClick = {},
            onSettingClick = { isSettingsVisible = true },
            onEmotionBubbleClick = {},
            locationPermissionController = locationDependencies.permissionController,
            appSettingsLauncher = appSettingsLauncher,
            modifier = Modifier.fillMaxSize(),
        )

        EmotionDetailOverlay(
            detailState,
            detailViewModel::dismiss,
            detailViewModel::retry,
            audioRepository,
            detailViewModel::toggleReaction,
        )

        if (isSettingsVisible) {
            SettingsScreen(
                appVersion = appVersion,
                onBackClick = { isSettingsVisible = false },
                permissionSettingsLauncher = permissionSettingsLauncher,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
