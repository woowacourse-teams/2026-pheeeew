package com.pheeeew.data.local.group

import com.pheeeew.domain.repository.group.LastRecordedGroupRepository
import platform.Foundation.NSUserDefaults

class IosLastRecordedGroupRepository(
    private val defaults: NSUserDefaults,
) : LastRecordedGroupRepository {
    override suspend fun readGroupId(): String? = defaults.stringForKey(KEY)

    override suspend fun writeGroupId(groupId: String?) {
        if (groupId == null) defaults.removeObjectForKey(KEY) else defaults.setObject(groupId, forKey = KEY)
    }

    private companion object {
        const val KEY = "last_recorded_group_id"
    }
}
