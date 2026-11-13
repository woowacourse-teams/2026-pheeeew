package com.pheeeew.core.notification

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock

internal class IosRevisitNotificationScheduler : RevisitNotificationScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun cancelOwned() {
        val pendingIds = pendingRequests().map { it.identifier }.filter(::owns)
        center.removePendingNotificationRequestsWithIdentifiers(pendingIds)
        val deliveredIds =
            suspendCoroutine { continuation ->
                center.getDeliveredNotificationsWithCompletionHandler { notifications ->
                    continuation.resume(
                        notifications
                            .orEmpty()
                            .filterIsInstance<UNNotification>()
                            .map { it.request.identifier }
                            .filter(::owns),
                    )
                }
            }
        center.removeDeliveredNotificationsWithIdentifiers(deliveredIds)
    }

    override suspend fun schedule(plan: RevisitNotificationPlan) {
        try {
            // Keep the batch within a conservative 64-slot budget, including other features.
            check(pendingRequests().size + plan.fireTimesEpochMillis.size <= 64) {
                "Not enough notification slots for revisit plan"
            }
            plan.fireTimesEpochMillis.forEachIndexed { index, fireTime ->
                currentCoroutineContext().ensureActive()
                val delayMillis = fireTime - Clock.System.now().toEpochMilliseconds()
                if (delayMillis <= 0) return@forEachIndexed
                val identifier = "${PREFIX}${plan.lastAccessEpochMillis}.$index"
                val content =
                    UNMutableNotificationContent().apply {
                        setTitle("히유~ 요즘 어떻게 지내요?")
                        setBody("쌓인 마음이 있다면, 히유에서 가볍게 털어놔요.")
                        setSound(UNNotificationSound.defaultSound)
                        setUserInfo(
                            mapOf(
                                "schemaVersion" to "1",
                                "type" to "REVISIT_REMINDER",
                                "notificationId" to identifier,
                            ),
                        )
                    }
                val request =
                    UNNotificationRequest.requestWithIdentifier(
                        identifier,
                        content,
                        UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(delayMillis / 1000.0, false),
                    )
                // Await the native completion even on cancellation so an old add cannot
                // finish after a newer plan has acquired the coordinator's mutex.
                suspendCoroutine { continuation ->
                    center.addNotificationRequest(request) { error ->
                        if (error == null) {
                            continuation.resume(Unit)
                        } else {
                            continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
        } catch (error: Exception) {
            cancelOwned()
            throw error
        }
    }

    private suspend fun pendingRequests(): List<UNNotificationRequest> =
        suspendCoroutine { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                continuation.resume(requests.orEmpty().filterIsInstance<UNNotificationRequest>())
            }
        }

    companion object {
        private const val PREFIX = "pheeeew.revisit."

        fun owns(identifier: String): Boolean = identifier.startsWith(PREFIX)
    }
}
