package com.pheeeew.feature.screens.group.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.detail.component.GroupDetailNoticeSnackbar
import com.pheeeew.feature.screens.group.detail.component.InviteCodeDialog
import com.pheeeew.feature.screens.group.detail.component.LeaveGroupDialog
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_back
import pheeeew.shared.generated.resources.group_detail_copy_failed
import pheeeew.shared.generated.resources.group_detail_copy_succeeded
import pheeeew.shared.generated.resources.group_detail_emotion_failed
import pheeeew.shared.generated.resources.group_detail_load_error_body
import pheeeew.shared.generated.resources.group_detail_load_error_title
import pheeeew.shared.generated.resources.group_detail_loading
import pheeeew.shared.generated.resources.group_detail_membership_changed_body
import pheeeew.shared.generated.resources.group_detail_membership_changed_title
import pheeeew.shared.generated.resources.group_detail_menu_leave
import pheeeew.shared.generated.resources.group_detail_more
import pheeeew.shared.generated.resources.group_detail_retry
import pheeeew.shared.generated.resources.group_detail_return_home
import pheeeew.shared.generated.resources.group_home_error_illustration
import pheeeew.shared.generated.resources.group_home_title
import pheeeew.shared.generated.resources.ic_arrow_back

@Composable
fun GroupDetailScreen(
    uiState: GroupDetailUiState,
    actions: GroupDetailActions,
    modifier: Modifier = Modifier,
) {
    val title = uiState.detail?.group?.name ?: uiState.groupName ?: stringResource(Res.string.group_home_title)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GroupDetailTopBar(
                title = title,
                overlay = uiState.overlay,
                actions = actions,
            )
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

                is GroupDetailContent.Ready -> {
                    GroupDetailReadyContent(
                        detail = content.detail,
                        isRefreshing = uiState.isRefreshing,
                        hasRefreshError = uiState.hasRefreshError,
                        canTapEmotion = uiState.canTapEmotion,
                        onInviteClick = actions.onInviteClick,
                        onInviteShareClick = actions.onInviteShareClick,
                        onRetry = actions.onRetry,
                        onEmotionTap = actions.onEmotionTap,
                    )
                }
            }
        }

        uiState.notice?.let { notice ->
            val message =
                when (notice.kind) {
                    GroupDetailNoticeKind.CopySucceeded -> stringResource(Res.string.group_detail_copy_succeeded)
                    GroupDetailNoticeKind.CopyFailed -> stringResource(Res.string.group_detail_copy_failed)
                    GroupDetailNoticeKind.EmotionUnavailable -> stringResource(Res.string.group_detail_emotion_failed)
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
                    onDismiss = actions.onDismissOverlay,
                )
            }
        }

        GroupDetailOverlay.LeaveConfirm,
        GroupDetailOverlay.LeaveFailed,
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
        -> {}
    }
}

@Composable
private fun GroupDetailTopBar(
    title: String,
    overlay: GroupDetailOverlay,
    actions: GroupDetailActions,
) {
    val moreDescription = stringResource(Res.string.group_detail_more)
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_arrow_back),
            contentDescription = stringResource(Res.string.group_detail_back),
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = actions.onBack)
                    .padding(9.dp),
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            color = AppColors.GroupInk,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
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
                        center =
                            center.copy(
                                y =
                                    center.y + offset.dp.toPx(),
                            ),
                    )
                }
            }
            if (overlay == GroupDetailOverlay.Menu) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = AppColors.GroupInk)
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
    DetailUnavailableContent(
        title = stringResource(Res.string.group_detail_load_error_title),
        body = stringResource(Res.string.group_detail_load_error_body),
        actionLabel = stringResource(Res.string.group_detail_retry),
        onAction = onRetry,
    )
}

/** 이미 나간 그룹의 이전 화면에 재진입했을 때 표시하며 그룹 목록으로 이동합니다. */
@Composable
private fun MembershipChangedContent(onReturnHome: () -> Unit) {
    DetailUnavailableContent(
        title = stringResource(Res.string.group_detail_membership_changed_title),
        body = stringResource(Res.string.group_detail_membership_changed_body),
        actionLabel = stringResource(Res.string.group_detail_return_home),
        onAction = onReturnHome,
    )
}

@Composable
private fun DetailUnavailableContent(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(188.dp))
        Image(
            painter = painterResource(Res.drawable.group_home_error_illustration),
            contentDescription = null,
            modifier = Modifier.size(width = 138.dp, height = 116.dp),
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
