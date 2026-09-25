package com.pheeeew.feature.screens.group.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/**
 * 상태 수집과 그룹 내부 콜백 연결을 담당합니다. 앱 navigation은 호출자가 소유합니다.
 * [onReturnHome]은 이전 화면으로 pop하는 대신 그룹 목록까지 이동하고 탈퇴한 그룹의 경로를 제거합니다.
 */
@Composable
fun GroupDetailRoute(
    viewModel: GroupDetailViewModel,
    isCurrentDestination: Boolean = true,
    onBack: () -> Unit,
    onReturnHome: () -> Unit,
    onInviteClick: (GroupId) -> Unit,
    onLeft: (groupId: GroupId, operationKey: GroupOperationKey) -> Unit,
    onCopyCode: suspend (code: String, operationKey: GroupOperationKey) -> GroupCopyCodeResult,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnReturnHome by rememberUpdatedState(onReturnHome)
    val currentOnInviteClick by rememberUpdatedState(onInviteClick)
    val currentOnLeft by rememberUpdatedState(onLeft)
    val currentOnCopyCode by rememberUpdatedState(onCopyCode)

    LaunchedEffect(lifecycleOwner, viewModel, isCurrentDestination) {
        if (!isCurrentDestination) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onRefresh()
            viewModel.uiState.collect { state ->
                val result = state.overlay as? GroupDetailOverlay.Left ?: return@collect
                currentOnLeft(viewModel.groupId, result.operationKey)
                viewModel.acknowledgeLeft(result.operationKey)
            }
        }
    }

    val copyRequest = uiState.copyRequest
    LaunchedEffect(lifecycleOwner, viewModel, isCurrentDestination, copyRequest?.operationKey) {
        if (!isCurrentDestination || copyRequest == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val result =
                try {
                    currentOnCopyCode(copyRequest.code, copyRequest.operationKey)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    GroupCopyCodeResult.Unavailable
                }
            viewModel.onCopyResult(copyRequest.operationKey, result)
        }
    }

    val notice = uiState.notice
    LaunchedEffect(lifecycleOwner, viewModel, isCurrentDestination, notice?.operationKey) {
        if (!isCurrentDestination || notice == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(NOTICE_DURATION_MILLIS)
            viewModel.acknowledgeNotice(notice.operationKey)
        }
    }

    GroupDetailScreen(
        uiState = uiState,
        actions =
            GroupDetailActions(
                onBack = {
                    if (viewModel.onBackRequested()) currentOnBack()
                },
                onReturnHome = { currentOnReturnHome() },
                onRetry = viewModel::onRetry,
                onMoreClick = viewModel::onMoreClick,
                onInviteClick = viewModel::onInviteClick,
                onInviteShareClick = { currentOnInviteClick(viewModel.groupId) },
                onCopyCodeClick = viewModel::onCopyCodeClick,
                onDismissOverlay = viewModel::onDismissOverlay,
                onLeaveMenuClick = viewModel::onLeaveMenuClick,
                onConfirmLeave = viewModel::onConfirmLeave,
                onRetryLeave = viewModel::onRetryLeave,
                onResolveLeaveOutcome = viewModel::onResolveLeaveOutcome,
                onEmotionTap = viewModel::onEmotionTap,
                onNoticeDismissed = viewModel::acknowledgeNotice,
            ),
        modifier = modifier,
    )
}

private const val NOTICE_DURATION_MILLIS = 2_000L
