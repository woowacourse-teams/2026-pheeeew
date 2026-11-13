package com.pheeeew

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal object AndroidNotificationChannels {
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channelId = context.getString(R.string.remote_notification_channel_id)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.remote_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.remote_notification_channel_description)
                },
            )
        }
        val revisitId = context.getString(R.string.revisit_notification_channel_id)
        if (manager.getNotificationChannel(revisitId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    revisitId,
                    context.getString(R.string.revisit_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.revisit_notification_channel_description)
                },
            )
        }
    }
}
