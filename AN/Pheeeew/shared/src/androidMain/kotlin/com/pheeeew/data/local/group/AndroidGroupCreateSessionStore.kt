package com.pheeeew.data.local.group

import android.content.Context
import com.pheeeew.feature.screens.group.create.GroupCreateSessionSnapshot
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
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

    private companion object {
        const val PREFERENCES_NAME = "group_create_session"
        const val KEY = "session_v1"
    }
}
