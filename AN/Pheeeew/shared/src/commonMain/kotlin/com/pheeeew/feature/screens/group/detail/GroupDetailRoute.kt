package com.pheeeew.feature.screens.group.detail

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
import com.pheeeew.core.share.rememberSystemTextShareLauncher
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.monitoring.product.rememberObservedEmotionPlayer
import com.pheeeew.feature.screens.group.join.GroupInviteLinkCodec
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/**
 * 상태 수집과 그룹 내부 콜백 연결을 담당합니다. 앱 navigation은 호출자가 소유합니다.
 * [onReturnHome]은 이전 화면으로 pop하는 대신 그룹 목록까지 이동하고 탈퇴한 그룹 경로를 제거합니다.
 */
@Composable
fun GroupDetailRoute(
    viewModel: GroupDetailViewModel,
    isCurrentDestination: Boolean,
    onBack: () -> Unit,
    onReturnHome: () -> Unit,
    onLeft: (groupId: GroupId, operationKey: GroupOperationKey) -> Unit,
    /** Invalidate the home snapshot and remove this ID before the view model acknowledges the event. */
    onMembershipUnavailable: (
        groupId: GroupId,
        reason: GroupDetailAccessLoss,
        operationKey: GroupOperationKey,
    ) -> Unit,
    onCopyCode: suspend (code: String, operationKey: GroupOperationKey) -> GroupCopyCodeResult,
    modifier: Modifier = Modifier,
    onMoodBlockClick: ((String) -> Unit)?,
    onMoodReportClick: ((String) -> Unit)?,
) {
    ProductScreen(viewModel.telemetry, isCurrentDestination)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shareLauncher = rememberSystemTextShareLauncher()
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioPlayer = rememberObservedEmotionPlayer(viewModel.telemetry) { "group_detail" }
    val playback by audioPlayer.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnReturnHome by rememberUpdatedState(onReturnHome)
    val currentOnLeft by rememberUpdatedState(onLeft)
    val currentOnMembershipUnavailable by rememberUpdatedState(onMembershipUnavailable)
    val currentOnCopyCode by rememberUpdatedState(onCopyCode)

    LaunchedEffect(lifecycleOwner, viewModel, audioPlayer, isCurrentDestination) {
        if (!isCurrentDestination) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { event ->
                when (event) {
                    is GroupDetailEvent.PlayMoodAudio -> audioPlayer.play(event.emotionId, event.url)
                    GroupDetailEvent.StopMoodAudio -> audioPlayer.stop()
                }
            }
        }
    }

    LaunchedEffect(viewModel, playback.id) {
        viewModel.onMoodAudioPlaybackChanged(playback.id)
    }

    DisposableEffect(lifecycleOwner, audioPlayer) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) audioPlayer.stop()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioPlayer.stop()
        }
    }

    LaunchedEffect(lifecycleOwner, viewModel, isCurrentDestination) {
        if (!isCurrentDestination) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResumed()
            viewModel.uiState.collect { state ->
                state.membershipEvent?.let { event ->
                    currentOnMembershipUnavailable(viewModel.groupId, event.reason, event.operationKey)
                    viewModel.acknowledgeMembershipEvent(event.operationKey)
                }
                (state.overlay as? GroupDetailOverlay.Left)?.let { result ->
                    currentOnLeft(viewModel.groupId, result.operationKey)
                    viewModel.acknowledgeLeft(result.operationKey)
                }
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
                onCopyCodeClick = viewModel::onCopyCodeClick,
                onShareInviteClick = {
                    val current = viewModel.uiState.value
                    val detail = current.detail
                    if (detail != null && detail.role != GroupRole.NONE &&
                        current.overlay == GroupDetailOverlay.InviteCode
                    ) {
                        val message = GroupInviteLinkCodec.createShareMessage(detail.group.name, detail.inviteCode)
                        if (message == null || !shareLauncher.shareText(message)) {
                            viewModel.onInviteShareUnavailable()
                        }
                    }
                },
                onDismissOverlay = viewModel::onDismissOverlay,
                onLeaveMenuClick = viewModel::onLeaveMenuClick,
                onConfirmLeave = viewModel::onConfirmLeave,
                onRetryLeave = viewModel::onRetryLeave,
                onResolveLeaveOutcome = viewModel::onResolveLeaveOutcome,
                onNoticeDismissed = viewModel::acknowledgeNotice,
                onMoodReactionClick = viewModel::onMoodReactionClick,
                onMoodAudioClick = viewModel::onMoodAudioClick,
                onMoodBlockClick = onMoodBlockClick,
                onMoodReportClick = onMoodReportClick,
                onMoodFeedRetry = viewModel::onMoodFeedRetry,
                onMoodFeedLoadMore = viewModel::onMoodFeedLoadMore,
            ),
        modifier = modifier,
    )
}

private const val NOTICE_DURATION_MILLIS = 2_000L
