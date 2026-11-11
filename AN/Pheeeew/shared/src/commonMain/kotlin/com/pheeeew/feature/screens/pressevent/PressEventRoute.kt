package com.pheeeew.feature.screens.pressevent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.audio.rememberPressSoundPlayer
import com.pheeeew.core.designsystem.component.DarkSystemBarsEffect
import com.pheeeew.core.navigation.FlowBackHandler
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun PressEventRoute(
    eventId: String?,
    sessionGeneration: Long?,
    onBack: () -> Unit,
) {
    val viewModel =
        assistedMetroViewModel<PressEventViewModel, PressEventViewModel.Factory> { create(eventId, sessionGeneration) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sound = rememberPressSoundPlayer()
    LifecycleResumeEffect(viewModel, sound, eventId to sessionGeneration) {
        viewModel.onContextChanged(eventId, sessionGeneration)
        viewModel.onScreenResumed()
        sound.prepare()
        onPauseOrDispose {
            viewModel.onScreenHidden()
            sound.stop()
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.onScreenHidden() } }
    FlowBackHandler(enabled = state.game.phase != PressEventPhase.Ready, onBack = viewModel::returnToRanking)
    if (state.game.phase != PressEventPhase.Ready) DarkSystemBarsEffect()
    if (state.game.phase == PressEventPhase.Ready) {
        PressEventRankingScreen(
            state = state,
            onStart = viewModel::start,
            onBack = onBack,
            onRefresh = viewModel::refreshRankings,
            onLoadMore = viewModel::loadMore,
        )
    } else {
        PressEventScreen(
            state = state.game,
            submission = state.submission,
            result = state.result,
            onRetrySubmission = viewModel::retrySubmission,
            onStart = viewModel::start,
            onBack = viewModel::returnToRanking,
            timer = { PressEventTimerRoute(viewModel.remainingMillis) },
            onPress = { kind ->
                val accepted = viewModel.press(kind)
                if (accepted) runCatching { sound.play() }
                accepted
            },
        )
    }
}

/** Collect the fast clock here so ticks do not invalidate the screen or button pad. */
@Composable
private fun PressEventTimerRoute(remainingMillis: StateFlow<Long>) {
    val remaining by remainingMillis.collectAsStateWithLifecycle()
    EventTimer(remaining, EventPressColors.Ink)
}
