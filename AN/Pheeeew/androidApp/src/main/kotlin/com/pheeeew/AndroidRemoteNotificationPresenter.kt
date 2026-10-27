package com.pheeeew

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

/** Foreground notification+data messages use the same system channel and tap route as background FCM. */
internal class AndroidRemoteNotificationPresenter(
    context: Context,
) {
    private val context = context.applicationContext

    fun show(message: RemoteMessage) {
        val content = message.notification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val id = message.data["notificationId"] ?: message.messageId ?: UUID.randomUUID().toString()
        val intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("pheeeew://notification/remote/${Uri.encode(id)}"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        ROUTING_FIELDS.forEach { key -> message.data[key]?.let { intent.putExtra(key, it) } }
        intent.putExtra("notificationId", id)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, context.getString(R.string.remote_notification_channel_id))
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        try {
            manager.notify("remote-$id", 0, notification)
        } catch (_: SecurityException) {
            // Authorization may be revoked between the snapshot above and posting.
        }
    }

    private companion object {
        val ROUTING_FIELDS = setOf("type", "date", "previousRank", "currentRank", "collectionId", "schemaVersion")
    }
}
