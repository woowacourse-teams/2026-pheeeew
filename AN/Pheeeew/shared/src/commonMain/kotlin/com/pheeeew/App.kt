package com.pheeeew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.di.ApiDependencies
import com.pheeeew.core.di.LocationDependencies
import com.pheeeew.core.di.createEmotionAudioRepository
import com.pheeeew.core.di.createEmotionDetailRepository
import com.pheeeew.core.di.createEmotionMapDependencies
import com.pheeeew.core.di.createEmotionModerationDependencies
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.MapScreen
import com.pheeeew.feature.screens.map.MapViewModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailOverlay
import com.pheeeew.feature.screens.map.detail.EmotionDetailViewModel
import com.pheeeew.feature.screens.map.record.MapRecordViewModel
import com.pheeeew.feature.screens.onboarding.OnboardingScreen
import com.pheeeew.feature.screens.report.ReportRoute
import com.pheeeew.feature.screens.settings.SettingsScreen
import com.pheeeew.legacy.core.permission.LocationPermissionSettingsLauncher
import org.jetbrains.compose.resources.DrawableResource

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
    val detailRepository = remember(apiDependencies.client) { createEmotionDetailRepository(apiDependencies.client) }
    val detailViewModel: EmotionDetailViewModel =
        viewModel { EmotionDetailViewModel(detailRepository) }
    val detailState by detailViewModel.uiModel.collectAsState()
    val audioRepository = remember { createEmotionAudioRepository() }
    val moderation = remember(apiDependencies.client) { createEmotionModerationDependencies(apiDependencies.client) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<Long, DrawableResource>?>(null) }
    var moderationMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapScreen(
            viewModel = mapViewModel,
            recordViewModel = mapRecordViewModel,
            onEmotionPinClick = detailViewModel::open,
            onListClick = {},
            onSettingClick = { isSettingsVisible = true },
            onEmotionBubbleClick = {},
            modifier = Modifier.fillMaxSize(),
        )

        EmotionDetailOverlay(
            detailState,
            detailViewModel::dismiss,
            detailViewModel::retry,
            audioRepository,
            detailViewModel::toggleReaction,
            moderation.block,
            onReportClick = { id, stamp ->
                detailViewModel.dismiss()
                reportTarget = id to stamp
            },
            onBlockSucceeded = {
                detailViewModel.dismiss()
                mapViewModel.refreshEmotionPins()
                moderationMessage = "차단되었습니다."
            },
        )

        Snackbar(
            message = moderationMessage,
            onDismiss = { moderationMessage = null },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
        )

        reportTarget?.let { (id, stamp) ->
            ReportRoute(
                emotionId = id,
                emotionStamp = stamp,
                reportEmotion = moderation.report,
                onBack = { reportTarget = null },
            )
        }

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
