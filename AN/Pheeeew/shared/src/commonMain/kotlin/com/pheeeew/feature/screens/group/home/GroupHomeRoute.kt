package com.pheeeew.feature.screens.group.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.feature.screens.group.join.GroupJoinSheet
import com.pheeeew.feature.screens.group.join.GroupJoinSubmissionState
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.flow.collect

/** ViewModel 상태 수집과 화면 콜백 연결을 담당합니다. 실제 navigation은 호출자가 소유합니다. */
@Composable
fun GroupHomeRoute(
    viewModel: GroupHomeViewModel,
    isCurrentDestination: Boolean = true,
    onCreateClick: () -> Unit,
    onGroupClick: (GroupId) -> Unit,
    onJoinSucceeded: (groupId: GroupId, operationKey: GroupOperationKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val joinUiState by viewModel.joinUiState.collectAsStateWithLifecycle()
    val isJoinSheetVisible by viewModel.isJoinSheetVisible.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnJoinSucceeded by rememberUpdatedState(onJoinSucceeded)

    DisposableEffect(lifecycleOwner, viewModel, isCurrentDestination) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (isCurrentDestination && event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfDirty()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner, viewModel, isCurrentDestination) {
        if (!isCurrentDestination) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.joinUiState.collect { state ->
                val result = state.submission as? GroupJoinSubmissionState.Succeeded ?: return@collect
                currentOnJoinSucceeded(result.groupId, result.operationKey)
                viewModel.consumeJoinAndClose(result.operationKey)
            }
        }
    }

    GroupHomeScreen(
        uiState = uiState,
        onCreateClick = onCreateClick,
        onJoinClick = viewModel::openJoinSheet,
        onGroupClick = onGroupClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )

    if (isJoinSheetVisible) {
        GroupJoinSheet(
            uiState = joinUiState,
            onCodeChanged = viewModel::onJoinCodeChanged,
            onSearchClick = viewModel::onJoinSearchClick,
            onJoinClick = viewModel::onJoinClick,
            onDismiss = { viewModel.closeJoinSheet() },
        )
    }
}
