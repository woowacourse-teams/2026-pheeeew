package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupPreview

/** Reads a group preview by invitation code and joins using that code. */
interface GroupJoinRepository {
    suspend fun findByInviteCode(normalizedCode: String): GroupInviteLookupResult

    suspend fun join(normalizedCode: String): GroupJoinRepositoryResult
}

sealed interface GroupInviteLookupResult {
    data class Found(
        val group: GroupPreview,
    ) : GroupInviteLookupResult

    data object NotFound : GroupInviteLookupResult

    data class RateLimited(
        val retryAfterMillis: Long?,
    ) : GroupInviteLookupResult

    data object Unavailable : GroupInviteLookupResult
}

sealed interface GroupJoinRepositoryResult {
    data class Joined(
        val groupId: GroupId,
    ) : GroupJoinRepositoryResult

    data object InviteCodeNotFound : GroupJoinRepositoryResult

    data object AlreadyMember : GroupJoinRepositoryResult

    data class RateLimited(
        val retryAfterMillis: Long?,
    ) : GroupJoinRepositoryResult

    data object Rejected : GroupJoinRepositoryResult

    data object Unavailable : GroupJoinRepositoryResult

    /** The server may have applied the join, so the caller must reconcile membership before retrying. */
    data object OutcomeUnknown : GroupJoinRepositoryResult
}
