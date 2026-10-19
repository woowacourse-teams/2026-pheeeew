package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.repository.GroupPublicJoinResult
import com.pheeeew.feature.screens.group.model.GroupId

fun interface JoinPublicGroupAction {
    suspend fun join(groupId: GroupId): GroupPublicJoinResult
}
