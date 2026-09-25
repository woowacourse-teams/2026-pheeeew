package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.screens.group.model.GroupId

/** 생성 화면의 초안을 공급 경계에 전달하는 값 객체입니다. */
data class GroupCreateDraft(
    val name: String,
    val description: String,
    val stamp: StampAppearanceUiModel,
)

/** 그룹 생성 시나리오를 수행하는 Feature 내부 경계입니다. 서버 DTO를 노출하지 않습니다. */
fun interface CreateGroupAction {
    suspend fun create(draft: GroupCreateDraft): CreateGroupResult
}

/** 예기치 않은 생성 오류를 ViewModel 경계에서 한 번 기록하는 포트입니다. */
fun interface GroupCreateErrorReporter {
    fun reportUnexpected(error: Exception)
}

sealed interface CreateGroupResult {
    data class Created(val groupId: GroupId) : CreateGroupResult

    data object DuplicateName : CreateGroupResult

    data object Unavailable : CreateGroupResult

    /** 요청 제한 시간이 끝나 서버 반영 여부를 알 수 없는 상태입니다. 자동 재요청하면 안 됩니다. */
    data object OutcomeUnknown : CreateGroupResult
}

/** 서버 전송 직전 한 곳에서 입력을 정규화합니다. */
internal fun GroupCreateDraft.normalizedForSubmission(): GroupCreateDraft =
    copy(
        name = name.trim(),
        description = description.trim(),
        stamp = stamp.copy(label = stamp.label.trim()),
    )

/** 생성 동작의 제한 시간입니다. 기본값은 API 정책 확정 전 개발 설정입니다. */
data class CreateRequestPolicy(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(timeoutMillis > 0L) { "timeoutMillis는 양수여야 합니다." }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
