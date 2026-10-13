package com.pheeeew.feature.screens.group.home.explore

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** Supplies one page of the public group catalog. This boundary is deliberately independent of the API adapter. */
interface GroupExploreSource {
    suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): GroupExplorePageResult
}

data class GroupExploreItemUiModel(
    val group: GroupSummaryUiModel,
    val joinType: GroupJoinType,
    val isJoined: Boolean,
)

data class GroupExplorePage(
    val items: List<GroupExploreItemUiModel>,
    val nextCursor: String?,
) {
    val hasNextPage: Boolean
        get() = nextCursor != null
}

sealed interface GroupExplorePageResult {
    data class Success(
        val page: GroupExplorePage,
    ) : GroupExplorePageResult

    data object Unavailable : GroupExplorePageResult
}

/** In-memory catalog for the screen-only sprint. Replace this provider when the explore API is ready. */
class InMemoryGroupExploreSource(
    private val groups: List<GroupExploreItemUiModel> = demoGroups,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : GroupExploreSource {
    init {
        require(pageSize > 0) { "페이지 크기는 1 이상이어야 합니다." }
    }

    override suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): GroupExplorePageResult {
        val normalizedKeyword = normalizeGroupExploreKeyword(keyword.orEmpty())
        val filteredGroups =
            groups
                .asSequence()
                .filter { item ->
                    normalizedKeyword == null || item.group.name.contains(normalizedKeyword, ignoreCase = true)
                }.toList()
        val offset =
            when {
                cursor == null -> {
                    0
                }

                cursor.startsWith(CURSOR_PREFIX) -> {
                    cursor.removePrefix(CURSOR_PREFIX).toIntOrNull()
                        ?: return GroupExplorePageResult.Unavailable
                }

                else -> {
                    return GroupExplorePageResult.Unavailable
                }
            }
        if (offset < 0 || offset > filteredGroups.size) return GroupExplorePageResult.Unavailable

        val pageItems = filteredGroups.drop(offset).take(pageSize)
        val nextOffset = offset + pageItems.size
        return GroupExplorePageResult.Success(
            GroupExplorePage(
                items = pageItems,
                nextCursor = nextOffset.takeIf { it < filteredGroups.size }?.let { "$CURSOR_PREFIX$it" },
            ),
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 3
        private const val CURSOR_PREFIX = "demo:"

        private val demoGroups =
            listOf(
                demoGroup(
                    "demo-hike",
                    "산책 친구",
                    "퇴근 후 가볍게 걷고, 하루를 나눠요.",
                    "산책",
                    12,
                    GroupJoinType.PUBLIC,
                    false,
                    0xFF9DE8D0L,
                ),
                demoGroup(
                    "demo-weekend",
                    "주말 산책",
                    "주말 아침 함께 걷는 우리 모임",
                    "주말",
                    8,
                    GroupJoinType.PRIVATE,
                    false,
                    0xFFFFD879L,
                ),
                demoGroup(
                    "demo-neighborhood",
                    "동네 산책 모임",
                    "작은 걸음도 함께 기록해요.",
                    "동네",
                    24,
                    GroupJoinType.PUBLIC,
                    true,
                    0xFF9DE8D0L,
                ),
                demoGroup(
                    "demo-book",
                    "저녁 독서 모임",
                    "읽은 문장과 오늘의 기분을 기록해요.",
                    "독서",
                    6,
                    GroupJoinType.PRIVATE,
                    true,
                    0xFFFFC8A8L,
                ),
                demoGroup(
                    "demo-coffee",
                    "커피 한 잔",
                    "매일의 작은 쉼표를 함께 나눠요.",
                    "커피",
                    18,
                    GroupJoinType.PUBLIC,
                    false,
                    0xFFE0C5FFL,
                ),
                demoGroup(
                    "demo-long-name",
                    "오래오래 이어가는 우리 동네 기록 모임",
                    "서로의 하루를 천천히 모아요.",
                    "우리",
                    31,
                    GroupJoinType.PRIVATE,
                    false,
                    0xFFF3B9D1L,
                ),
                demoGroup("demo-garden", "초록 정원", "오늘 발견한 초록을 나눠요.", "초록", 10, GroupJoinType.PUBLIC, true, 0xFFB9E6A5L),
                demoGroup("demo-night", "밤 산책 기록", "하루 끝에 짧게 걸어요.", "밤", 4, GroupJoinType.PUBLIC, false, 0xFFAFC8F5L),
            )

        private fun demoGroup(
            id: String,
            name: String,
            description: String,
            stampLabel: String,
            memberCount: Long,
            joinType: GroupJoinType,
            isJoined: Boolean,
            fillArgb: Long,
        ) = GroupExploreItemUiModel(
            group =
                GroupSummaryUiModel(
                    id = GroupId(id),
                    name = name,
                    memberCount = memberCount,
                    weeklyStampCount = null,
                    stamp =
                        StampAppearanceUiModel(
                            label = stampLabel,
                            shape = StampShapeId.ROUNDED_RECTANGLE,
                            fillArgb = fillArgb,
                            textArgb = 0xFF15181BL,
                        ),
                    description = description,
                ),
            joinType = joinType,
            isJoined = isJoined,
        )
    }
}

fun normalizeGroupExploreKeyword(keyword: String): String? = keyword.trim().takeIf(String::isNotEmpty)
