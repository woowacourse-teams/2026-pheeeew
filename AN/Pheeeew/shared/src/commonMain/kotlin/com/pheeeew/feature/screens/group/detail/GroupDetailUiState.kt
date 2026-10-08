package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.model.GroupOperationKey

data class GroupDetailUiState(
    val content: GroupDetailContent = GroupDetailContent.Loading,
    val isLoading: Boolean = false,
    val isLoadingIndicatorVisible: Boolean = false,
    val refreshStatus: GroupDetailRefreshStatus = GroupDetailRefreshStatus.Idle,
    val overlay: GroupDetailOverlay = GroupDetailOverlay.None,
    val copyRequest: GroupCopyCodeRequest? = null,
    val notice: GroupDetailNotice? = null,
    val groupName: String? = null,
    val membershipEvent: GroupDetailMembershipEvent? = null,
    val moodFeed: GroupMoodFeedUiState = GroupMoodFeedUiState.Loading,
) {
    val detail: GroupDetailUiModel?
        get() = (content as? GroupDetailContent.Ready)?.detail

    val isRefreshing: Boolean
        get() = refreshStatus == GroupDetailRefreshStatus.Refreshing

    val hasRefreshError: Boolean
        get() = refreshStatus == GroupDetailRefreshStatus.Failed

    init {
        require(!isLoadingIndicatorVisible || isLoading) {
            "로딩 중일 때만 로딩 인디케이터를 표시할 수 있습니다."
        }
        require(content is GroupDetailContent.Ready || refreshStatus == GroupDetailRefreshStatus.Idle) {
            "상세 스냅샷이 없을 때는 새로고침 상태를 가질 수 없습니다."
        }
        require(copyRequest == null || overlay == GroupDetailOverlay.InviteCode) {
            "초대코드 복사 요청은 초대코드 팝업이 열린 동안만 유지합니다."
        }
    }
}

sealed interface GroupDetailContent {
    data object Loading : GroupDetailContent

    data class Ready(
        val detail: GroupDetailUiModel,
    ) : GroupDetailContent

    data object LoadFailed : GroupDetailContent

    data object MembershipChanged : GroupDetailContent

    data object NotFound : GroupDetailContent
}

enum class GroupDetailRefreshStatus {
    Idle,
    Refreshing,
    Failed,
}

sealed interface GroupDetailOverlay {
    data object None : GroupDetailOverlay

    data object Menu : GroupDetailOverlay

    data object InviteCode : GroupDetailOverlay

    data object LeaveConfirm : GroupDetailOverlay

    data class Leaving(
        val operationKey: GroupOperationKey,
    ) : GroupDetailOverlay

    data object LeaveFailed : GroupDetailOverlay

    data object LeaveStillMember : GroupDetailOverlay

    data object OwnerCannotLeave : GroupDetailOverlay

    /** 나가기 요청 결과가 시간 초과 등으로 불명확합니다. 재전송 대신 멤버십을 다시 확인합니다. */
    data object LeaveOutcomeUnknown : GroupDetailOverlay

    data class Left(
        val operationKey: GroupOperationKey,
    ) : GroupDetailOverlay
}

data class GroupCopyCodeRequest(
    val operationKey: GroupOperationKey,
    val code: String,
)

data class GroupDetailNotice(
    val operationKey: GroupOperationKey,
    val kind: GroupDetailNoticeKind,
)

enum class GroupDetailNoticeKind {
    CopySucceeded,
    CopyFailed,
}

data class GroupDetailMembershipEvent(
    val operationKey: GroupOperationKey,
    val reason: GroupDetailAccessLoss,
)
