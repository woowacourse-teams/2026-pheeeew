package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.group.GroupPressRepository
import com.pheeeew.domain.repository.group.GroupWeeklyPressCountResult
import com.pheeeew.feature.screens.group.detail.GroupDetailWeeklyPressCountResult
import com.pheeeew.feature.screens.group.detail.GroupDetailWeeklyPressCountSource
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

/** Adapts the group-scoped weekly aggregate endpoint independently from the cross-group ranking. */
class ApiGroupDetailWeeklyPressCountSource(
    private val repository: GroupPressRepository,
) : GroupDetailWeeklyPressCountSource {
    override suspend fun load(groupId: GroupId): GroupDetailWeeklyPressCountResult {
        val domainGroupId = DomainGroupId.parse(groupId.value) ?: return GroupDetailWeeklyPressCountResult.Unavailable
        return when (val result = repository.findWeekly(domainGroupId)) {
            is GroupWeeklyPressCountResult.Loaded -> GroupDetailWeeklyPressCountResult.Loaded(result.total)
            GroupWeeklyPressCountResult.Unavailable -> GroupDetailWeeklyPressCountResult.Unavailable
        }
    }
}
