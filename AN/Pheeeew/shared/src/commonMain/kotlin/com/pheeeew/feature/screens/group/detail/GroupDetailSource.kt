package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupId

fun interface GroupDetailSource {
    suspend fun load(groupId: GroupId): GroupDetailLoadResult
}

sealed interface GroupDetailLoadResult {
    data class Loaded(
        val detail: GroupDetailUiModel,
    ) : GroupDetailLoadResult

    data object MembershipChanged : GroupDetailLoadResult

    data object NotFound : GroupDetailLoadResult

    data object Unavailable : GroupDetailLoadResult
}

enum class GroupDetailAccessLoss {
    MembershipChanged,
    NotFound,
}

fun interface GroupDetailErrorReporter {
    fun reportUnexpected(exception: Exception)
}

data class GroupDetailRequestPolicy(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(timeoutMillis > 0L) { "timeoutMillis는 양수여야 합니다." }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
