package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.group.GroupExploreRepository
import com.pheeeew.domain.repository.group.GroupExploreResult
import com.pheeeew.feature.screens.group.home.explore.GroupExploreItemUiModel
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePage
import com.pheeeew.feature.screens.group.home.explore.GroupExplorePageResult
import com.pheeeew.feature.screens.group.home.explore.GroupExploreSource
import com.pheeeew.feature.screens.group.mapper.toAppearanceUiModel
import com.pheeeew.feature.screens.group.mapper.toUiJoinType
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

class ApiGroupExploreSource(
    private val repository: GroupExploreRepository,
) : GroupExploreSource {
    override suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): GroupExplorePageResult =
        when (val result = repository.loadPage(keyword, cursor)) {
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
