package com.pheeeew.data.local.group

import android.content.Context
import com.pheeeew.domain.repository.group.LastRecordedGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLastRecordedGroupRepository(
    context: Context,
) : LastRecordedGroupRepository {
    private val preferences = context.applicationContext.getSharedPreferences("record_group", Context.MODE_PRIVATE)

    override suspend fun readGroupId(): String? =
        withContext(Dispatchers.IO) {
            preferences.getString(KEY, null)
        }

    override suspend fun writeGroupId(groupId: String?) {
        withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            if (groupId == null) editor.remove(KEY) else editor.putString(KEY, groupId)
            check(editor.commit()) { "Failed to save the last recorded group" }
        }
    }

    private companion object {
        const val KEY = "last_group_id"
    }
}
