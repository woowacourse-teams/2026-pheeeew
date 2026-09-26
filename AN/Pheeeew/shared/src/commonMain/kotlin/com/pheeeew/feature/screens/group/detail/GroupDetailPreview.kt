package com.pheeeew.feature.screens.group.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailCopyKey
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupRankUiModel
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.screens.group.preview.HomeFixtures

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 신규 그룹")
@Composable
private fun GroupDetailFirstStartPreview() {
    GroupDetailPreviewFrame(
        detail = fixtureDetail(todayTotal = 0L, presentation = GroupDetailPresentationKind.FirstStart),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 감정과 횟수")
@Composable
private fun GroupDetailActivePreview() {
    GroupDetailInteractivePreview()
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 최초 로딩")
@Composable
private fun GroupDetailLoadingPreview() {
    GroupDetailPreviewFrame(state = GroupDetailUiState(content = GroupDetailContent.Loading))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 조회 오류")
@Composable
private fun GroupDetailLoadFailedPreview() {
    GroupDetailPreviewFrame(
        state = GroupDetailUiState(content = GroupDetailContent.LoadFailed, groupName = "우테코 8기 히유"),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 참여 상태 변경")
@Composable
private fun GroupDetailMembershipChangedPreview() {
    GroupDetailPreviewFrame(
        state = GroupDetailUiState(content = GroupDetailContent.MembershipChanged, groupName = "우테코 8기 히유"),
    )
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 더보기 메뉴")
@Composable
private fun GroupDetailMenuPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.Menu)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 초대코드")
@Composable
private fun GroupDetailInvitePreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.InviteCode)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 나가기 확인")
@Composable
private fun GroupDetailLeaveConfirmPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.LeaveConfirm)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 나가는 중")
@Composable
private fun GroupDetailLeavingPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.Leaving(GroupOperationKey("preview", 1L)))
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 나가기 실패")
@Composable
private fun GroupDetailLeaveFailedPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.LeaveFailed)
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 - 결과 확인 필요")
@Composable
private fun GroupDetailLeaveOutcomeUnknownPreview() {
    GroupDetailPreviewFrame(overlay = GroupDetailOverlay.LeaveOutcomeUnknown)
}

@Composable
private fun GroupDetailInteractivePreview() {
    var previewDetail by remember {
        mutableStateOf(fixtureDetail(todayTotal = 1_238L, presentation = GroupDetailPresentationKind.Active))
    }
    GroupDetailScreen(
        uiState = GroupDetailUiState(content = GroupDetailContent.Ready(previewDetail)),
        actions =
            previewActions(onEmotionTap = { emotion ->
                previewDetail =
                    previewDetail.copy(
                        todayTotal = previewDetail.todayTotal + 1,
                        emotionCounts =
                            previewDetail.emotionCounts.map { count ->
                                if (count.kind == emotion) count.copy(count = count.count + 1) else count
                            },
                    )
                true
            }),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun GroupDetailPreviewFrame(
    detail: GroupDetailUiModel = fixtureDetail(todayTotal = 1_238L, presentation = GroupDetailPresentationKind.Active),
    state: GroupDetailUiState = GroupDetailUiState(content = GroupDetailContent.Ready(detail)),
    overlay: GroupDetailOverlay = GroupDetailOverlay.None,
) {
    GroupDetailScreen(
        uiState = state.copy(overlay = overlay),
        actions = previewActions(),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun previewActions(onEmotionTap: (EmotionKind) -> Boolean = { true }) =
    GroupDetailActions(
        onBack = {},
        onReturnHome = {},
        onRetry = {},
        onMoreClick = {},
        onInviteClick = {},
        onInviteShareClick = {},
        onCopyCodeClick = {},
        onDismissOverlay = {},
        onLeaveMenuClick = {},
        onConfirmLeave = {},
        onRetryLeave = {},
        onResolveLeaveOutcome = {},
        onEmotionTap = onEmotionTap,
        onNoticeDismissed = {},
    )

private fun fixtureDetail(
    todayTotal: Long,
    presentation: GroupDetailPresentationKind,
): GroupDetailUiModel =
    GroupDetailUiModel(
        group =
            HomeFixtures.groups.first().let { group ->
                if (presentation == GroupDetailPresentationKind.FirstStart) {
                    group.copy(name = "우테코 8기 히유", memberCount = 12, weeklyStampCount = 0L)
                } else {
                    group.copy(name = "우테코 8기 히유", memberCount = 12, weeklyStampCount = 128L)
                }
            },
        emotionCounts =
            EmotionKind.entries.mapIndexed { index, emotion ->
                val count =
                    if (presentation ==
                        GroupDetailPresentationKind.FirstStart
                    ) {
                        0L
                    } else {
                        DEFAULT_EMOTION_COUNTS[index]
                    }
                EmotionCountUiModel(emotion, count)
            },
        todayTotal = todayTotal,
        rank =
            if (presentation ==
                GroupDetailPresentationKind.FirstStart
            ) {
                GroupRankUiModel.Unranked
            } else {
                GroupRankUiModel.Ranked(2)
            },
        inviteCode = "HIYU26",
        presentation =
            GroupDetailPresentationUiModel(
                kind = presentation,
                heroTitle =
                    if (presentation == GroupDetailPresentationKind.FirstStart) {
                        GroupDetailCopyKey.FirstStartHeroTitle
                    } else {
                        GroupDetailCopyKey.ActiveHeroTitle
                    },
                heroSubtitle =
                    if (presentation == GroupDetailPresentationKind.FirstStart) {
                        GroupDetailCopyKey.FirstStartHeroSubtitle
                    } else {
                        GroupDetailCopyKey.ActiveHeroSubtitle
                    },
                summaryMessage =
                    if (presentation == GroupDetailPresentationKind.Active) {
                        GroupDetailCopyKey.SummaryBlocked
                    } else {
                        null
                    },
            ),
    )

private val DEFAULT_EMOTION_COUNTS = listOf(428L, 312L, 246L, 154L, 98L)
