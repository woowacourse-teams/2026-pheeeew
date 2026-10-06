package com.pheeeew.feature.screens.group.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.AppPopup
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.DetailTopBar
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.detail.component.GroupDetailNoticeSnackbar
import com.pheeeew.feature.screens.group.detail.component.InviteCodeDialog
import com.pheeeew.feature.screens.group.detail.component.LeaveGroupDialog
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_back
import pheeeew.shared.generated.resources.group_detail_copy_failed
import pheeeew.shared.generated.resources.group_detail_copy_succeeded
import pheeeew.shared.generated.resources.group_detail_emotion_failed
import pheeeew.shared.generated.resources.group_detail_invite_share_unavailable
import pheeeew.shared.generated.resources.group_detail_loading
import pheeeew.shared.generated.resources.group_detail_membership_changed_body
import pheeeew.shared.generated.resources.group_detail_membership_changed_title
import pheeeew.shared.generated.resources.group_detail_menu_leave
import pheeeew.shared.generated.resources.group_detail_more
import pheeeew.shared.generated.resources.group_detail_not_found_body
import pheeeew.shared.generated.resources.group_detail_not_found_title
import pheeeew.shared.generated.resources.group_detail_press_blocked
import pheeeew.shared.generated.resources.group_detail_press_cooldown
import pheeeew.shared.generated.resources.group_detail_press_failed
import pheeeew.shared.generated.resources.group_detail_press_queue_full
import pheeeew.shared.generated.resources.group_detail_press_rate_limited
import pheeeew.shared.generated.resources.group_detail_press_rate_limited_retry
import pheeeew.shared.generated.resources.group_detail_return_home
import pheeeew.shared.generated.resources.group_home_title
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted

@Composable
fun GroupDetailScreen(
    uiState: GroupDetailUiState,
    actions: GroupDetailActions,
    modifier: Modifier = Modifier,
    fixtureFeedbackOnAcceptedPress: Boolean = false,
    feedbackOperationKey: () -> com.pheeeew.feature.screens.group.model.GroupOperationKey? = { null },
    onFeedbackShown: (com.pheeeew.feature.screens.group.model.GroupOperationKey) -> Unit = {},
) {
    val title = uiState.detail?.group?.name ?: uiState.groupName ?: stringResource(Res.string.group_home_title)
    val pullState = rememberPullToRefreshState()
    val pullDistance = with(LocalDensity.current) { 56.dp.toPx() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRetry,
            state = pullState,
            enabled =
                uiState.overlay == GroupDetailOverlay.None && uiState.pressStatus == GroupPressStatus.Idle &&
                    uiState.content is GroupDetailContent.Ready,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = AppColors.Surface,
                    color = AppColors.Primary,
                )
            },
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        translationY = pullState.distanceFraction.coerceIn(0f, 1f) * pullDistance
                    },
            ) {
                GroupDetailTopBar(
                    title = title,
                    overlay = uiState.overlay,
                    actions = actions,
                )
                Box(modifier = Modifier.weight(1f)) {
                    when (val content = uiState.content) {
                        GroupDetailContent.Loading -> {
                            LoadingContent()
                        }

                        GroupDetailContent.LoadFailed -> {
                            FailedContent(onRetry = actions.onRetry)
                        }

                        GroupDetailContent.MembershipChanged -> {
                            MembershipChangedContent(onReturnHome = actions.onReturnHome)
                        }

                        GroupDetailContent.NotFound -> {
                            NotFoundContent(onReturnHome = actions.onReturnHome)
                        }

                        is GroupDetailContent.Ready -> {
                            GroupDetailReadyContent(
                                detail = content.detail,
                                hasRefreshError = uiState.hasRefreshError,
                                isRefreshing = uiState.isRefreshing,
                                canTapEmotion = uiState.canTapEmotion,
                                pressStatus = uiState.pressStatus,
                                pendingEmotionPresses = uiState.pendingEmotionPresses,
                                unconfirmedEmotionPresses = uiState.unconfirmedEmotionPresses,
                                emotionRanking = uiState.emotionRanking,
                                weeklyPressCount = uiState.weeklyPressCount,
                                onInviteClick = actions.onInviteClick,
                                onRetry = actions.onRetry,
                                onEmotionTap = actions.onEmotionTap,
                                onResolvePressOutcome = actions.onResolvePressOutcome,
                                onRetryEmotionRanking = actions.onRetryEmotionRanking,
                                fixtureFeedbackOnAcceptedPress = fixtureFeedbackOnAcceptedPress,
                                feedbackOperationKey = feedbackOperationKey,
                                onFeedbackShown = onFeedbackShown,
                            )
                        }
                    }
                }
            }
        }

        uiState.notice?.let { notice ->
            val message =
                when (notice.kind) {
                    GroupDetailNoticeKind.CopySucceeded -> {
                        stringResource(Res.string.group_detail_copy_succeeded)
                    }

                    GroupDetailNoticeKind.CopyFailed -> {
                        stringResource(Res.string.group_detail_copy_failed)
                    }

                    GroupDetailNoticeKind.InviteShareUnavailable -> {
                        stringResource(Res.string.group_detail_invite_share_unavailable)
                    }

                    GroupDetailNoticeKind.PressRejected -> {
                        stringResource(Res.string.group_detail_press_failed)
                    }

                    GroupDetailNoticeKind.PressUnavailable -> {
                        stringResource(Res.string.group_detail_emotion_failed)
                    }

                    GroupDetailNoticeKind.PressBlockedWhilePending -> {
                        stringResource(Res.string.group_detail_press_blocked)
                    }

                    GroupDetailNoticeKind.PressQueueFull -> {
                        stringResource(Res.string.group_detail_press_queue_full)
                    }

                    GroupDetailNoticeKind.PressRateLimited -> {
                        val retryAfterMillis = notice.retryAfterMillis
                        if (retryAfterMillis == null) {
                            stringResource(Res.string.group_detail_press_rate_limited)
                        } else {
                            val retryAfterSeconds =
                                (retryAfterMillis / 1_000L + if (retryAfterMillis % 1_000L == 0L) 0L else 1L)
                                    .coerceAtMost(Int.MAX_VALUE.toLong())
                                    .toInt()
                            stringResource(Res.string.group_detail_press_rate_limited_retry, retryAfterSeconds)
                        }
                    }
                }
            GroupDetailNoticeSnackbar(
                message = message,
                kind = notice.kind,
                onDismiss = { actions.onNoticeDismissed(notice.operationKey) },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            )
        }
    }

    when (uiState.overlay) {
        GroupDetailOverlay.InviteCode -> {
            val detail = uiState.detail
            if (detail != null) {
                InviteCodeDialog(
                    code = detail.inviteCode,
                    isCopying = uiState.copyRequest != null,
                    onCopy = actions.onCopyCodeClick,
                    onShare = actions.onShareInviteClick,
                    onDismiss = actions.onDismissOverlay,
                )
            }
        }

        GroupDetailOverlay.LeaveConfirm,
        GroupDetailOverlay.LeaveFailed,
        GroupDetailOverlay.LeaveStillMember,
        GroupDetailOverlay.OwnerCannotLeave,
        GroupDetailOverlay.LeaveOutcomeUnknown,
        is GroupDetailOverlay.Leaving,
        is GroupDetailOverlay.Left,
        -> {
            LeaveGroupDialog(
                overlay = uiState.overlay,
                onDismiss = actions.onDismissOverlay,
                onConfirm = actions.onConfirmLeave,
                onRetry = actions.onRetryLeave,
                onResolveOutcome = actions.onResolveLeaveOutcome,
            )
        }

        GroupDetailOverlay.None,
        GroupDetailOverlay.Menu,
        -> {
        }
    }
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 화면")
@Composable
private fun GroupDetailScreenPreview() {
    GroupDetailScreen(
        uiState =
            GroupDetailUiState(
                content =
                    GroupDetailContent.Ready(
                        fixtureDetail(1_238L, GroupDetailPresentationKind.Active),
                    ),
            ),
        actions = previewActions(),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun GroupDetailTopBar(
    title: String,
    overlay: GroupDetailOverlay,
    actions: GroupDetailActions,
) {
    val moreDescription = stringResource(Res.string.group_detail_more)
    DetailTopBar(
        title = title,
        onBack = actions.onBack,
        backContentDescription = stringResource(Res.string.group_detail_back),
        rightContent = {
            androidx.compose.foundation.Canvas(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = actions.onMoreClick)
                        .semantics { contentDescription = moreDescription },
            ) {
                listOf(-8f, 0f, 8f).forEach { offset ->
                    drawCircle(
                        AppColors.GroupInk,
                        radius = 1.8.dp.toPx(),
                        center = center.copy(y = center.y + offset.dp.toPx()),
                    )
                }
            }
            if (overlay == GroupDetailOverlay.Menu) {
                AppPopup(
                    alignment = Alignment.TopEnd,
                    offset = with(LocalDensity.current) { IntOffset(0, 48.dp.roundToPx()) },
                    onDismissRequest = actions.onDismissOverlay,
                    properties =
                        androidx.compose.ui.window
                            .PopupProperties(focusable = true),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(158.dp)
                                .height(44.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = actions.onLeaveMenuClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.group_detail_menu_leave),
                            color = Color(0xFFFF3B30),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularLoadingIndicator(color = AppColors.GroupInk)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.group_detail_loading),
            color = AppColors.RankingSecondaryContent,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun FailedContent(onRetry: () -> Unit) {
    LoadErrorContent(
        onRetry = onRetry,
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 25.dp, vertical = 96.dp),
    )
}

/** 이미 나간 그룹의 이전 화면에 재진입했을 때 표시하며 그룹 목록으로 이동합니다. */
@Composable
private fun MembershipChangedContent(onReturnHome: () -> Unit) {
    DetailUnavailableContent(
        title = stringResource(Res.string.group_detail_membership_changed_title),
        body = stringResource(Res.string.group_detail_membership_changed_body),
        actionLabel = stringResource(Res.string.group_detail_return_home),
        illustration = Res.drawable.ic_emotion_exhausted,
        onAction = onReturnHome,
    )
}

@Composable
private fun NotFoundContent(onReturnHome: () -> Unit) {
    DetailUnavailableContent(
        title = stringResource(Res.string.group_detail_not_found_title),
        body = stringResource(Res.string.group_detail_not_found_body),
        actionLabel = stringResource(Res.string.group_detail_return_home),
        illustration = Res.drawable.ic_emotion_discouraged,
        onAction = onReturnHome,
    )
}

@Composable
private fun DetailUnavailableContent(
    title: String,
    body: String,
    actionLabel: String,
    illustration: DrawableResource,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(188.dp))
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = title,
            color = AppColors.GroupInk,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(text = body, color = Color(0xFF7B817B), fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(38.dp))
        DetailOutlineButton(text = actionLabel, onClick = onAction, modifier = Modifier.width(228.dp))
        Spacer(Modifier.height(24.dp))
    }
}
