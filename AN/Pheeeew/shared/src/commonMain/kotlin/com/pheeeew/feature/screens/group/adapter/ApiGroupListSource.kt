package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.group.GroupListLoadResult
import com.pheeeew.domain.repository.group.GroupListRepository
import com.pheeeew.feature.screens.group.home.GroupListResult
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.mapper.toSummaryUiModel

/** Adapts the domain repository to the existing home screen boundary. */
class ApiGroupListSource(
    private val repository: GroupListRepository,
) : GroupListSource {
    override suspend fun loadGroups(): GroupListResult =
        when (val result = repository.findMine()) {
            is GroupListLoadResult.Loaded -> GroupListResult.Success(result.groups.map { it.toSummaryUiModel() })
            GroupListLoadResult.Unavailable -> GroupListResult.Unavailable
        }
}
