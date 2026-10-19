package com.pheeeew.feature.screens.group.adapter

import com.pheeeew.domain.repository.GroupPublicJoinRepository
import com.pheeeew.domain.repository.GroupPublicJoinResult
import com.pheeeew.feature.screens.group.detail.JoinPublicGroupAction
import com.pheeeew.feature.screens.group.model.GroupId
import dev.zacsweers.metro.Inject
import com.pheeeew.domain.model.group.GroupId as DomainGroupId

@Inject
class ApiPublicGroupJoinAction(
    private val repository: GroupPublicJoinRepository,
) : JoinPublicGroupAction {
    override suspend fun join(groupId: GroupId): GroupPublicJoinResult {
        val id = DomainGroupId.parse(groupId.value) ?: return GroupPublicJoinResult.Unavailable
        return repository.join(id)
    }
}
