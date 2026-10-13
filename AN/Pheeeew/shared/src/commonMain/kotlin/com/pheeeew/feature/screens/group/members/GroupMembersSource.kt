package com.pheeeew.feature.screens.group.members

import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.model.GroupId

/** Provides one page of the current group's member list. The API adapter is intentionally deferred. */
interface GroupMembersSource {
    suspend fun loadPage(
        groupId: GroupId,
        cursor: String?,
    ): GroupMembersPageResult
}

data class GroupMemberUiModel(
    /** Nullable and blank values are rendered as 익명. The endpoint does not expose a member ID. */
    val nickname: String?,
    val role: GroupRole,
    /** Kept for the API order contract; the screen does not display a date. */
    val joinedAt: String,
) {
    init {
        require(role != GroupRole.NONE) { "멤버 목록 항목은 OWNER 또는 MEMBER여야 합니다." }
        require(joinedAt.isNotBlank()) { "가입 시각은 비어 있을 수 없습니다." }
    }

    val displayNickname: String
        get() = nickname?.trim()?.takeIf(String::isNotEmpty) ?: ANONYMOUS_NICKNAME
}

const val ANONYMOUS_NICKNAME = "익명"

data class GroupMembersPage(
    val items: List<GroupMemberUiModel>,
    val nextCursor: String?,
)

sealed interface GroupMembersPageResult {
    data class Success(
        val page: GroupMembersPage,
    ) : GroupMembersPageResult

    data object Forbidden : GroupMembersPageResult

    data object NotFound : GroupMembersPageResult

    data object Unavailable : GroupMembersPageResult
}

/** In-memory, API-free data used to exercise member ordering and paging in this sprint. */
class InMemoryGroupMembersSource(
    private val members: List<GroupMemberUiModel> = demoMembers,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val unavailableCursors: Set<String> = emptySet(),
) : GroupMembersSource {
    init {
        require(pageSize > 0) { "페이지 크기는 1 이상이어야 합니다." }
    }

    override suspend fun loadPage(
        groupId: GroupId,
        cursor: String?,
    ): GroupMembersPageResult {
        when (groupId.value) {
            FORBIDDEN_GROUP_ID -> return GroupMembersPageResult.Forbidden
            NOT_FOUND_GROUP_ID -> return GroupMembersPageResult.NotFound
        }
        if (cursor != null && cursor in unavailableCursors) return GroupMembersPageResult.Unavailable
        val offset =
            when {
                cursor == null -> 0
                cursor.startsWith(CURSOR_PREFIX) -> cursor.removePrefix(CURSOR_PREFIX).toIntOrNull()
                else -> null
            } ?: return GroupMembersPageResult.Unavailable
        if (offset < 0 || offset > members.size) return GroupMembersPageResult.Unavailable

        val pageItems = members.drop(offset).take(pageSize)
        val nextOffset = offset + pageItems.size
        return GroupMembersPageResult.Success(
            GroupMembersPage(
                items = pageItems,
                nextCursor = nextOffset.takeIf { it < members.size }?.let { "$CURSOR_PREFIX$it" },
            ),
        )
    }

    companion object {
        const val FORBIDDEN_GROUP_ID = "demo-members-forbidden"
        const val NOT_FOUND_GROUP_ID = "demo-members-not-found"

        private const val DEFAULT_PAGE_SIZE = 4
        private const val CURSOR_PREFIX = "demo:members:"

        private val demoMembers =
            listOf(
                member("산책하는 하루", GroupRole.MEMBER, "2026-09-01T08:00:00Z"),
                member(null, GroupRole.MEMBER, "2026-09-02T08:00:00Z"),
                member("모두의 기록", GroupRole.OWNER, "2026-09-03T08:00:00Z"),
                member("같은 닉네임", GroupRole.MEMBER, "2026-09-04T08:00:00Z"),
                member("같은 닉네임", GroupRole.MEMBER, "2026-09-05T08:00:00Z"),
                member("  ", GroupRole.MEMBER, "2026-09-06T08:00:00Z"),
                member("오늘도 천천히 걷고 감정을 기록하는 긴 닉네임 예시입니다", GroupRole.MEMBER, "2026-09-07T08:00:00Z"),
                member("작은 쉼표", GroupRole.MEMBER, "2026-09-08T08:00:00Z"),
            )

        private fun member(
            nickname: String?,
            role: GroupRole,
            joinedAt: String,
        ) = GroupMemberUiModel(nickname, role, joinedAt)
    }
}
