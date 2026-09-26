package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupOperationKey

data class GroupDetailUiState(
    val content: GroupDetailContent = GroupDetailContent.Loading,
    val refreshStatus: GroupDetailRefreshStatus = GroupDetailRefreshStatus.Idle,
    val overlay: GroupDetailOverlay = GroupDetailOverlay.None,
    val copyRequest: GroupCopyCodeRequest? = null,
    val notice: GroupDetailNotice? = null,
    val groupName: String? = null,
) {
    val detail: GroupDetailUiModel?
        get() = (content as? GroupDetailContent.Ready)?.detail

    val isRefreshing: Boolean
        get() = refreshStatus == GroupDetailRefreshStatus.Refreshing

    val hasRefreshError: Boolean
        get() = refreshStatus == GroupDetailRefreshStatus.Failed

    val canTapEmotion: Boolean
        get() = detail != null && overlay == GroupDetailOverlay.None

    init {
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

    /** 이미 나간 그룹으로 돌아온 경우를 포함해, 현재 참여 중이 아닌 상태입니다. */
    data object MembershipChanged : GroupDetailContent
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
    EmotionUnavailable,
}
