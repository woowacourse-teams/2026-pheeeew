package com.pheeeew.data.local.group

import com.pheeeew.feature.screens.group.create.GroupCreateSessionSnapshot
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.create.canDiscardCorruptedGroupCreateDraft
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

class IosGroupCreateSessionStore(
    private val defaults: NSUserDefaults,
) : GroupCreateSessionStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): GroupCreateSessionSnapshot? = defaults.stringForKey(KEY)?.let(json::decodeFromString)

    override suspend fun write(snapshot: GroupCreateSessionSnapshot?) {
        if (snapshot == null) {
            defaults.removeObjectForKey(KEY)
        } else {
            defaults.setObject(json.encodeToString(snapshot), forKey = KEY)
        }
    }

    override suspend fun clearCorruptedDraftIfSafe(): Boolean {
        val rawSnapshot = defaults.stringForKey(KEY) ?: return true
        if (!canDiscardCorruptedGroupCreateDraft(rawSnapshot, json)) return false
        defaults.removeObjectForKey(KEY)
        return true
    }

    private companion object {
        const val KEY = "group_create_session_v1"
    }
}
