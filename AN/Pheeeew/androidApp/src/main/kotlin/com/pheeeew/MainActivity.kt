package com.pheeeew

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.notification.NotificationClickInbox
import com.pheeeew.core.notification.NotificationMessageInbox
import com.pheeeew.core.notification.NotificationPermissionResultListener
import java.util.UUID

class MainActivity :
    ComponentActivity(),
    NotificationPermissionResultListener {
    override fun onNotificationPermissionResult() {
        (application as PheeeewApplication).notificationRuntime.permissionChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appGraph = (application as PheeeewApplication).appGraph
        receiveNotificationIntent(intent)
        val owner = ActivityRetainedGraphOwner.get(this, appGraph)
        owner.attach(this)
        val notifications = (application as PheeeewApplication).notificationRuntime
        setContent { AppEntry(this, appGraph, owner, notifications.clickInbox, notifications.messageInbox) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as PheeeewApplication).notificationRuntime.permissionChanged()
    }

    private fun receiveNotificationIntent(intent: Intent?) {
        val activityIntent = intent
        val extras = activityIntent?.extras
        if (activityIntent != null && extras != null && extras.getString("type") != null) {
            val hasStableId =
                extras.getString("notificationId") != null ||
                    extras.getString("google.message_id") != null ||
                    extras.getString("message_id") != null
            if (!hasStableId) {
                activityIntent.putExtra("notificationId", UUID.randomUUID().toString())
                setIntent(activityIntent)
            }
        }
        (application as PheeeewApplication).notificationRuntime.receiveClickIntent(activityIntent)
    }
}
