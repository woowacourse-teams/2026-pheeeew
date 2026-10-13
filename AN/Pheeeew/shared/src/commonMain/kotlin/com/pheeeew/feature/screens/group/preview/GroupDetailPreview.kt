package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.detail.GroupDetailActions
import com.pheeeew.feature.screens.group.detail.GroupDetailContent
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinFailure
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinPhase
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinUiState
import com.pheeeew.feature.screens.group.detail.GroupDetailOverlay
import com.pheeeew.feature.screens.group.detail.GroupDetailScreen
import com.pheeeew.feature.screens.group.detail.GroupDetailUiState
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.feature.screens.group.model.GroupOperationKey

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세")
@Composable
private fun GroupDetailPreview() {
    GroupDetailPreviewFrame(previewGroupDetailUiModel(GroupRole.MEMBER))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 그룹장")
@Composable
private fun GroupDetailOwnerPreview() {
    GroupDetailPreviewFrame(previewGroupDetailUiModel(GroupRole.OWNER))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 비공개 그룹 멤버")
@Composable
private fun GroupDetailPrivateMemberPreview() {
    GroupDetailPreviewFrame(previewGroupDetailUiModel(GroupRole.MEMBER))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 미가입")
@Composable
private fun GroupDetailGuestPreview() {
    GroupDetailPreviewFrame(previewGroupDetailUiModel(GroupRole.NONE))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 공개 그룹 참여")
@Composable
private fun GroupDetailPublicGuestPreview() {
    GroupDetailPreviewFrame(previewGroupDetailUiModel(GroupRole.NONE).copy(joinType = GroupJoinType.PUBLIC))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 공개 그룹 멤버")
@Composable
private fun GroupDetailPublicMemberPreview() {
    GroupDetailPreviewFrame(previewGroupDetailUiModel(GroupRole.MEMBER).copy(joinType = GroupJoinType.PUBLIC))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 순위 없음")
@Composable
private fun GroupDetailUnrankedPreview() {
    GroupDetailPreviewFrame(
        previewGroupDetailUiModel(GroupRole.MEMBER).copy(
            weeklyStampCount = 0,
            weeklyStampRank = null,
            weeklyEmotionPressCount = 0,
            weeklyEmotionPressRank = null,
        ),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 최초 로딩")
@Composable
private fun GroupDetailLoadingPreview() {
    GroupDetailPreviewFrame(
        state =
            GroupDetailUiState(
                content = GroupDetailContent.Loading,
                isLoading = true,
                isLoadingIndicatorVisible = true,
            ),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 조회 오류")
@Composable
private fun GroupDetailLoadFailedPreview() {
    GroupDetailPreviewFrame(
        state = GroupDetailUiState(content = GroupDetailContent.LoadFailed, groupName = "우테코 8기 히유"),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 재시도 중")
@Composable
private fun GroupDetailRetryingPreview() {
    GroupDetailPreviewFrame(
        state =
            GroupDetailUiState(
                content = GroupDetailContent.LoadFailed,
                isLoading = true,
                isLoadingIndicatorVisible = true,
                groupName = "우테코 8기 히유",
            ),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 참여 상태 변경")
@Composable
private fun GroupDetailMembershipChangedPreview() {
    GroupDetailPreviewFrame(
        state = GroupDetailUiState(content = GroupDetailContent.MembershipChanged, groupName = "우테코 8기 히유"),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 더보기 메뉴")
@Composable
private fun GroupDetailMenuPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.Menu)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 초대코드")
@Composable
private fun GroupDetailInvitePreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.InviteCode)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 나가기 확인")
@Composable
private fun GroupDetailLeaveConfirmPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.LeaveConfirm)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 나가는 중")
@Composable
private fun GroupDetailLeavingPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.Leaving(GroupOperationKey("preview", 1L)))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 나가기 실패")
@Composable
private fun GroupDetailLeaveFailedPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.LeaveFailed)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 · 결과 확인 필요")
@Composable
private fun GroupDetailLeaveOutcomeUnknownPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.LeaveOutcomeUnknown)
}

@Preview(name = "공개 참여 · 상태 확인 중", widthDp = 360, heightDp = 800)
@Composable
private fun GroupPublicJoinCheckingPreview() {
    publicJoinStatePreview(GroupDetailJoinPhase.Checking)
}

@Preview(name = "공개 참여 · 확인 실패", widthDp = 320, heightDp = 800)
@Composable
private fun GroupPublicJoinVerificationFailedPreview() {
    publicJoinStatePreview(GroupDetailJoinPhase.VerificationRequired)
}

@Composable
private fun publicJoinStatePreview(phase: GroupDetailJoinPhase) {
    val detail = previewGroupDetailUiModel(GroupRole.NONE).copy(joinType = GroupJoinType.PUBLIC)
    GroupDetailPreviewFrame(
        detail,
        state =
            GroupDetailUiState(
                content = GroupDetailContent.Ready(detail),
                join =
                    GroupDetailJoinUiState(
                        phase = phase,
                        failure =
                            if (phase ==
                                GroupDetailJoinPhase.VerificationRequired
                            ) {
                                GroupDetailJoinFailure.OutcomeUnknown
                            } else {
                                null
                            },
                    ),
            ),
    )
}

@Composable
private fun GroupDetailPreviewFrame(
    detail: GroupDetailUiModel = previewGroupDetailUiModel(GroupRole.MEMBER),
    state: GroupDetailUiState =
        GroupDetailUiState(
            content = GroupDetailContent.Ready(detail),
            moodFeed = previewGroupDetailReadyUiModel(detail.role).feed,
        ),
    overlay: GroupDetailOverlay = GroupDetailOverlay.None,
) {
    GroupDetailScreen(
        uiState = state.copy(overlay = overlay),
        actions = previewActions(),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun previewActions() =
    GroupDetailActions(
        onBack = {},
        onReturnHome = {},
        onRetry = {},
        onMoreClick = {},
        onInviteClick = {},
        onJoinClick = {},
        onCopyCodeClick = {},
        onShareInviteClick = {},
        onDismissOverlay = {},
        onLeaveMenuClick = {},
        onConfirmLeave = {},
        onRetryLeave = {},
        onResolveLeaveOutcome = {},
        onNoticeDismissed = {},
        onMoodReactionClick = null,
        onMoodAudioClick = null,
        onMoodBlockClick = null,
        onMoodReportClick = null,
        onMoodFeedRetry = null,
        onMoodFeedLoadMore = null,
    )

internal fun previewGroupDetailUiModel(role: GroupRole): GroupDetailUiModel =
    GroupDetailUiModel(
        group =
            HomeFixtures.groups.first().copy(
                name = "우테코 8기 히유",
                memberCount = 14,
                weeklyStampCount = null,
                description = "그룹 설명 문구를 여기에 넣을겁니다....\n그룹설명설명설명입니다다다다다이",
            ),
        role = role,
        inviteCode = if (role == GroupRole.NONE) null else "H1Y226",
        joinType = GroupJoinType.PRIVATE,
        weeklyStampCount = 42,
        weeklyStampRank = 3,
        weeklyEmotionPressCount = 318,
        weeklyEmotionPressRank = 5,
    )
