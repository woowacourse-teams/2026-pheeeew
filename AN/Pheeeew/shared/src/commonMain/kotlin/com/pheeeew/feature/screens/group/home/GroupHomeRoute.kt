package com.pheeeew.feature.screens.group.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.feature.screens.group.model.GroupId

/** ViewModel 상태 수집과 화면 콜백 연결을 담당합니다. 실제 navigation은 호출자가 소유합니다. */
@Composable
fun GroupHomeRoute(
    viewModel: GroupHomeViewModel,
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onGroupClick: (GroupId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfDirty()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GroupHomeScreen(
        uiState = uiState,
        onCreateClick = onCreateClick,
        onJoinClick = onJoinClick,
        onGroupClick = onGroupClick,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}
