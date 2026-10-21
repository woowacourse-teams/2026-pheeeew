package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.GroupExploreRepository
import com.pheeeew.domain.repository.GroupExploreResult
import com.pheeeew.feature.screens.group.home.explore.GroupExploreItemUiModel
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePage
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePageResult
import com.pheeeew.feature.screens.group.home.explore.GroupExploreSource
import com.pheeeew.feature.screens.group.home.explore.normalizeGroupExploreKeyword
import com.pheeeew.feature.screens.group.mapper.toAppearanceUiModel
import com.pheeeew.feature.screens.group.mapper.toUiJoinType
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import dev.zacsweers.metro.Inject

@Inject
class ApiGroupExploreSource(
    private val repository: GroupExploreRepository,
) : GroupExploreSource {
    override suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): GroupExplorePageResult {
        val normalizedKeyword = normalizeGroupExploreKeyword(keyword.orEmpty())
        if (normalizedKeyword != null && normalizedKeyword.length > MAX_GROUP_NAME_LENGTH) {
            return GroupExplorePageResult.Success(GroupExplorePage(emptyList(), null))
        }
        return when (val result = repository.loadPage(normalizedKeyword, cursor)) {
            is GroupExploreResult.Loaded -> {
                GroupExplorePageResult.Success(
                    GroupExplorePage(
                        result.page.items.map { item ->
                            val type = item.joinType.toUiJoinType()
                            GroupExploreItemUiModel(
                                group =
                                    GroupSummaryUiModel(
                                        id = GroupId(item.id.value),
                                        name = item.name,
                                        memberCount = item.memberCount,
                                        weeklyStampCount = null,
                                        stamp = item.stamp.toAppearanceUiModel(),
                                        description = item.description,
                                        joinType = type,
                                    ),
                                joinType = type,
                                isJoined = item.joined,
                            )
                        },
                        result.page.nextCursor,
                    ),
                )
            }

            GroupExploreResult.InvalidCursor -> {
                GroupExplorePageResult.InvalidCursor
            }

            GroupExploreResult.Unavailable -> {
                GroupExplorePageResult.Unavailable
            }
        }
    }

    private companion object {
        const val MAX_GROUP_NAME_LENGTH = 10
    }
}
