package com.pheeeew.core.notification

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RevisitNotificationStore {
    fun lastAccessEpochMillis(): Long?

    fun saveLastAccess(epochMillis: Long)
}

interface RevisitNotificationScheduler {
    /** Remove only this feature's pending and delivered notifications. */
    suspend fun cancelOwned()

    /** Register the whole plan or remove the partial plan on failure. */
    suspend fun schedule(plan: RevisitNotificationPlan)
}

class RevisitNotificationCoordinator(
    private val permissions: NotificationPermissionController,
    private val store: RevisitNotificationStore,
    private val scheduler: RevisitNotificationScheduler,
    private val policy: RevisitNotificationPolicy = RevisitNotificationPolicy(),
) {
    private val mutex = Mutex()

    suspend fun recordVisit(epochMillis: Long) {
        mutex.withLock {
            store.saveLastAccess(epochMillis)
            synchronize(epochMillis)
        }
    }

    /** Permission completion refreshes the existing visit; it is not a new visit. */
    suspend fun refresh() {
        mutex.withLock {
            val lastAccess = store.lastAccessEpochMillis() ?: return
            synchronize(lastAccess)
        }
    }

    private suspend fun synchronize(lastAccess: Long) {
        scheduler.cancelOwned()
        if (permissions.authorization().allowsNotifications()) {
            scheduler.schedule(policy.plan(lastAccess))
        }
    }
}
