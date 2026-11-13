package com.pheeeew

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

class PheeeewMessagingService : FirebaseMessagingService() {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val runtime = (application as? PheeeewApplication)?.notificationRuntime ?: return
        runBlocking { runtime.receiveTokenAndSchedule(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM posts background notification messages itself. Foreground messages reach this
        // callback and need an explicit system notification; the runtime only refreshes UI data.
        AndroidRemoteNotificationPresenter(this).show(message)
        (application as? PheeeewApplication)?.notificationRuntime?.receiveRemoteMessage(message)
    }
}
