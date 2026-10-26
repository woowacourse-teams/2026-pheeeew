package com.pheeeew.core.notification

import com.pheeeew.core.permission.IosAppSettingsLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock

/** Main-thread lifecycle entry points used by SwiftUI and the permission completion callback. */
object IosRevisitNotifications {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coordinator = createCoordinator(RevisitNotificationPolicy())
    private var nextOperationId = 0L
    private val jobs = mutableMapOf<Long, Job>()

    /** Called only by the Debug Swift entry point before the first lifecycle event. */
    fun useShortIntervalsForTesting() {
        check(NSBundle.mainBundle.objectForInfoDictionaryKey("MONITORING_ENVIRONMENT") == "dev")
        check(jobs.isEmpty())
        coordinator = createCoordinator(RevisitNotificationPolicy(60_000, 60_000, 30))
        println("재방문 알림 테스트 모드: 60초 후 첫 알림, 60초 간격, 최대 30회")
    }

    private fun createCoordinator(policy: RevisitNotificationPolicy) =
        RevisitNotificationCoordinator(
            IosNotificationPermissionController(IosAppSettingsLauncher()),
            IosRevisitNotificationStore(),
            IosRevisitNotificationScheduler(),
            policy,
        )

    fun recordVisit(onComplete: (Boolean) -> Unit): Long =
        launch(onComplete) { coordinator.recordVisit(Clock.System.now().toEpochMilliseconds()) }

    fun refresh() {
        launch({}) { coordinator.refresh() }
    }

    fun cancel(operationId: Long) {
        jobs[operationId]?.cancel()
    }

    fun isRevisit(identifier: String): Boolean = IosRevisitNotificationScheduler.owns(identifier)

    private fun launch(
        onComplete: (Boolean) -> Unit,
        action: suspend () -> Unit,
    ): Long {
        val id = ++nextOperationId
        val job =
            scope.launch {
                var succeeded = false
                try {
                    action()
                    succeeded = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    println("재방문 알림 예약 실패: 다음 접속 시 다시 동기화합니다.")
                } finally {
                    jobs.remove(id)
                    onComplete(succeeded)
                }
            }
        if (job.isActive) jobs[id] = job
        return id
    }
}

private class IosRevisitNotificationStore : RevisitNotificationStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val key = "notification.revisit.lastAccessEpochMillis"

    override fun lastAccessEpochMillis(): Long? =
        if (defaults.objectForKey(key) == null) null else defaults.doubleForKey(key).toLong()

    override fun saveLastAccess(epochMillis: Long) {
        defaults.setDouble(epochMillis.toDouble(), forKey = key)
    }
}
