package com.pheeeew.data.local.group

import android.content.Context
import com.pheeeew.feature.screens.group.create.GroupCreateSessionSnapshot
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.create.canDiscardCorruptedGroupCreateDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidGroupCreateSessionStore(
    context: Context,
) : GroupCreateSessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): GroupCreateSessionSnapshot? =
        withContext(Dispatchers.IO) {
            preferences.getString(KEY, null)?.let(json::decodeFromString)
        }

    override suspend fun write(snapshot: GroupCreateSessionSnapshot?) {
        withContext(Dispatchers.IO) {
            val editor = preferences.edit()
            if (snapshot == null) {
                editor.remove(KEY)
            } else {
                editor.putString(KEY, json.encodeToString(snapshot))
            }
            check(editor.commit()) { "Failed to save the group creation session" }
        }
    }

    override suspend fun clearCorruptedDraftIfSafe(): Boolean =
        withContext(Dispatchers.IO) {
            val rawSnapshot = preferences.getString(KEY, null) ?: return@withContext true
            if (!canDiscardCorruptedGroupCreateDraft(rawSnapshot, json)) return@withContext false
            check(preferences.edit().remove(KEY).commit()) { "Failed to clear the corrupted group creation draft" }
            true
        }

    private companion object {
        const val PREFERENCES_NAME = "group_create_session"
        const val KEY = "session_v1"
    }
}
