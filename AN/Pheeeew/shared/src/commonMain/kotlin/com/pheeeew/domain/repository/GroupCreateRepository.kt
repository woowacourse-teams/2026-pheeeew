package com.pheeeew.domain.repository

import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupStamp

data class GroupCreateCommand(
    val name: String,
    val description: String?,
    val stamp: GroupStamp,
)

fun interface GroupCreateRepository {
    suspend fun create(command: GroupCreateCommand): GroupCreateRepositoryResult
}

sealed interface GroupCreateRepositoryResult {
    data class Created(
        val groupId: GroupId,
    ) : GroupCreateRepositoryResult

    data object DuplicateName : GroupCreateRepositoryResult

    data object InvalidInput : GroupCreateRepositoryResult

    data object RateLimited : GroupCreateRepositoryResult

    data object Unavailable : GroupCreateRepositoryResult

    /** The server may have applied the write. A caller must reconcile before explicitly retrying. */
    data object OutcomeUnknown : GroupCreateRepositoryResult
}
