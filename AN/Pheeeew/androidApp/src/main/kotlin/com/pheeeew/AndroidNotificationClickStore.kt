package com.pheeeew

import android.content.Context
import android.util.Log
import com.pheeeew.core.notification.NotificationClick
import com.pheeeew.core.notification.NotificationClickSnapshot
import com.pheeeew.core.notification.NotificationClickStore
import com.pheeeew.core.notification.NotificationClickTarget
import com.pheeeew.core.notification.parseNotificationClick
import org.json.JSONArray
import org.json.JSONObject

/** Only normalized routing fields are stored; SDK extras and tokens never enter this file. */
internal class AndroidNotificationClickStore(
    context: Context,
    environment: String,
) : NotificationClickStore {
    private val preferences = context.getSharedPreferences("notification-clicks-$environment", Context.MODE_PRIVATE)

    override fun read(): NotificationClickSnapshot =
        try {
            val encoded = preferences.getString("snapshot", null)
            if (encoded == null) {
                NotificationClickSnapshot(preferences.getStringSet("acknowledged", emptySet()).orEmpty().toList())
            } else {
                val json = JSONObject(encoded)
                val ids = json.getJSONArray("ids")
                val pending =
                    json.optJSONObject("pending")?.let { envelope ->
                        val id = envelope.getString("id").takeIf { it.isNotBlank() } ?: return@let null
                        val data = envelope.getJSONObject("data")
                        parseNotificationClick(data.keys().asSequence().associateWith { data.getString(it) }, id)
                    }
                NotificationClickSnapshot((0 until ids.length()).map { ids.getString(it) }, pending)
            }
        } catch (_: Exception) {
            Log.w("NotificationRuntime", "Discarding invalid notification click snapshot")
            NotificationClickSnapshot()
        }

    override fun write(snapshot: NotificationClickSnapshot) {
        val json = JSONObject().put("ids", JSONArray(snapshot.rememberedIds))
        snapshot.pending?.let { json.put("pending", encode(it)) }
        if (!preferences
                .edit()
                .putString("snapshot", json.toString())
                .remove("acknowledged")
                .commit()
        ) {
            Log.w("NotificationRuntime", "Failed to persist notification click snapshot")
        }
    }

    private fun encode(click: NotificationClick): JSONObject {
        val fields =
            when (val target = click.target) {
                NotificationClickTarget.OpenApp -> {
                    emptyMap()
                }

                NotificationClickTarget.Revisit -> {
                    mapOf("type" to "REVISIT_REMINDER")
                }

                is NotificationClickTarget.CollectionClaimable -> {
                    mapOf(
                        "type" to "COLLECTION_CLAIMABLE",
                        "date" to target.date,
                    )
                }

                is NotificationClickTarget.Collection -> {
                    mapOf(
                        "type" to "COLLECTION_ISSUED",
                        "collectionId" to target.collectionId,
                    )
                }

                is NotificationClickTarget.Ranking -> {
                    mapOf(
                        "type" to "RANK_DROPPED",
                        "date" to target.date,
                        "previousRank" to target.previousRank.toString(),
                        "currentRank" to target.currentRank.toString(),
                    )
                }
            }
        return JSONObject().put("id", click.notificationId).put("data", JSONObject(fields))
    }
}
