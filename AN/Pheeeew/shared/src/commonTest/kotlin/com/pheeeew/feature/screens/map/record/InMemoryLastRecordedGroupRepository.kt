package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.repository.group.LastRecordedGroupRepository

class InMemoryLastRecordedGroupRepository(
    var groupId: String? = null,
) : LastRecordedGroupRepository {
    override suspend fun readGroupId(): String? = groupId

    override suspend fun writeGroupId(groupId: String?) {
        this.groupId = groupId
    }
}
