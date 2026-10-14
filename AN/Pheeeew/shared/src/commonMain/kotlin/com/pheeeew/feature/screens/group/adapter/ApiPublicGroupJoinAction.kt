package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.group.GroupPublicJoinRepository
import com.pheeeew.domain.repository.group.GroupPublicJoinResult
import com.pheeeew.feature.screens.group.detail.JoinPublicGroupAction
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

class ApiPublicGroupJoinAction(
    private val repository: GroupPublicJoinRepository,
) : JoinPublicGroupAction {
    override suspend fun join(groupId: GroupId): GroupPublicJoinResult {
        val id = DomainGroupId.parse(groupId.value) ?: return GroupPublicJoinResult.Unavailable
        return repository.join(id)
    }
}
