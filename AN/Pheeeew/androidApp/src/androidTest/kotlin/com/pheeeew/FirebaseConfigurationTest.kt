package com.pheeeew

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Checks packaged Firebase configuration and real FCM registration without logging the token. */
class FirebaseConfigurationTest {
    @Test
    fun firebaseInitializesAndIssuesMessagingToken() {
        val options = FirebaseApp.getInstance().options
        assertEquals("pheeeew-8bf12", options.projectId)
        assertEquals("481488887110", options.gcmSenderId)

        val token = Tasks.await(FirebaseMessaging.getInstance().token, 60, TimeUnit.SECONDS)
        assertTrue("FCM registration must return a non-empty token", token.isNotBlank())
    }
}
