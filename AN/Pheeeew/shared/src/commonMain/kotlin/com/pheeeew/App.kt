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
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionSheet
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel
import com.pheeeew.feature.screens.map.nearby.mock.createNearbyEmotionMockViewModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.onboarding.OnboardingScreen

@Composable
fun App(
    locationDependencies: LocationDependencies,
    apiDependencies: ApiDependencies,
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
    val mapViewModel: MapViewModel =
        viewModel {
            MapViewModel.create(locationDependencies)
        }
    val mapRecordViewModel: MapRecordViewModel =
        viewModel {
            MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase())
        }
    val nearbyViewModel: NearbyEmotionViewModel = viewModel { createNearbyEmotionMockViewModel() }
    Box(Modifier.fillMaxSize()) {
        MapScreen(
            viewModel = mapViewModel,
            recordViewModel = mapRecordViewModel,
            onListClick = nearbyViewModel::toggle,
            onViewportChanged = nearbyViewModel::onViewportChanged,
            onSettingClick = {},
            onEmotionBubbleClick = {},
            modifier = Modifier.fillMaxSize(),
        )
        NearbyEmotionSheet(
            nearbyViewModel,
            onEmotionHidden = mapViewModel::onEmotionHidden,
            onLeaveEmotion = mapViewModel::onEmotionSelectorOpen,
        )
    }
}
