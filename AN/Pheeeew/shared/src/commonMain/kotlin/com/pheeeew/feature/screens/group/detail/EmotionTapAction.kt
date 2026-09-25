package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupId

/** 현재 브랜치의 즉시 fixture 반응 경계입니다. 실제 네트워크 제출 계약으로 재사용하지 않습니다. */
fun interface EmotionTapAction {
    fun apply(
        groupId: GroupId,
        emotion: EmotionKind,
    ): EmotionTapResult
}

sealed interface EmotionTapResult {
    data class Applied(
        val detail: GroupDetailUiModel,
    ) : EmotionTapResult

    data object Unavailable : EmotionTapResult
}
