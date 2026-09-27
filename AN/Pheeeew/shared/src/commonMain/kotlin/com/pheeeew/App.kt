package com.pheeeew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.di.ApiDependencies
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.di.createEmotionMapDependencies
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapViewModel
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
    val mapRecordViewModel: MapRecordViewModel =
        viewModel {
            MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase())
        }
    var isSettingsVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            viewModel = mapViewModel,
            recordViewModel = mapRecordViewModel,
            onListClick = {},
            onSettingClick = { isSettingsVisible = true },
            onEmotionBubbleClick = {},
            modifier = Modifier.fillMaxSize(),
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
