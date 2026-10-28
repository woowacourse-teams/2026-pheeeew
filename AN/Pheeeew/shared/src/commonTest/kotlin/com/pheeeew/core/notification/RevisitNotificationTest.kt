package com.pheeeew.core.notification

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RevisitNotificationTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `48시간 후부터 30회 예약하며 마지막 알림은 접속 31일 후다`() {
        val times = RevisitNotificationPolicy().plan(123).fireTimesEpochMillis
        assertEquals(30, times.size)
        assertEquals(123 + 2 * day, times.first())
        assertEquals(123 + 31 * day, times.last())
        assertTrue(times.zipWithNext().all { (a, b) -> b - a == day })
    }

    @Test
    fun `짧은 시간 간격을 주입할 수 있고 잘못된 정책은 거부한다`() {
        val policy = RevisitNotificationPolicy(60_000, 60_000, 3)
        assertEquals(listOf(61_000L, 121_000L, 181_000L), policy.plan(1_000).fireTimesEpochMillis)
        assertFailsWith<IllegalArgumentException> { RevisitNotificationPolicy(count = 31) }
        assertFailsWith<IllegalArgumentException> { RevisitNotificationPolicy(intervalMillis = 0) }
    }

    @Test
    fun `재접속은 기존 예약을 새 기준의 30회로 교체한다`() =
        runTest {
            val store = Store()
            val scheduler = Scheduler()
            val model = RevisitNotificationCoordinator(Permissions(), store, scheduler)
            model.recordVisit(0)
            model.recordVisit(day)
            assertEquals(day, store.lastAccess)
            assertEquals(2, scheduler.cancellations)
            assertEquals(day + 2 * day, scheduler.plan?.fireTimesEpochMillis?.first())
            assertEquals(30, scheduler.plan?.fireTimesEpochMillis?.size)
        }

    @Test
    fun `차단 상태는 예약을 지우고 허용 후 저장된 접속 기준으로 복구한다`() =
        runTest {
            val permissions = Permissions()
            val store = Store()
            val scheduler = Scheduler()
            val model = RevisitNotificationCoordinator(permissions, store, scheduler)
            model.recordVisit(0)
            permissions.status = NotificationAuthorization.DENIED
            model.recordVisit(day)
            assertNull(scheduler.plan)
            assertEquals(day, store.lastAccess)
            permissions.status = NotificationAuthorization.AUTHORIZED
            model.refresh()
            assertEquals(day, scheduler.plan?.lastAccessEpochMillis)
        }

    @Test
    fun `첫 권한 허용은 방문 시각을 변경하지 않고 기존 방문을 동기화한다`() =
        runTest {
            val permissions = Permissions().apply { status = NotificationAuthorization.NOT_DETERMINED }
            val store = Store()
            val scheduler = Scheduler()
            val model = RevisitNotificationCoordinator(permissions, store, scheduler)
            model.recordVisit(123)
            assertNull(scheduler.plan)
            permissions.status = NotificationAuthorization.AUTHORIZED
            model.refresh()
            assertEquals(123L, store.lastAccess)
            assertEquals(123L, scheduler.plan?.lastAccessEpochMillis)
        }

    @Test
    fun `기록 없는 초기화는 예약하지 않고 재생성 후에는 저장한 방문을 복구한다`() =
        runTest {
            val store = Store()
            val scheduler = Scheduler()
            RevisitNotificationCoordinator(Permissions(), store, scheduler).refresh()
            assertEquals(0, scheduler.cancellations)
            store.lastAccess = 456
            RevisitNotificationCoordinator(Permissions(), store, scheduler).refresh()
            assertEquals(456L, scheduler.plan?.lastAccessEpochMillis)
        }

    @Test
    fun `예약 실패 후 다시 동기화해도 접속 시각은 유지된다`() =
        runTest {
            val store = Store()
            val scheduler = Scheduler().apply { fail = true }
            val model = RevisitNotificationCoordinator(Permissions(), store, scheduler)
            assertFailsWith<IllegalStateException> { model.recordVisit(789) }
            assertEquals(789L, store.lastAccess)
            scheduler.fail = false
            model.refresh()
            assertEquals(789L, scheduler.plan?.lastAccessEpochMillis)
        }

    @Test
    fun `겹치는 갱신은 직렬 처리하여 최신 접속 예약이 남는다`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val scheduler = Scheduler().apply { firstScheduleGate = gate }
            val store = Store()
            val model = RevisitNotificationCoordinator(Permissions(), store, scheduler)
            val first = launch { model.recordVisit(100) }
            runCurrent()
            val second = launch { model.recordVisit(200) }
            runCurrent()
            assertEquals(100L, store.lastAccess)
            gate.complete(Unit)
            first.join()
            second.join()
            assertEquals(200L, scheduler.plan?.lastAccessEpochMillis)
        }

    @Test
    fun `재방문 알림은 명시적인 타입과 고유 ID로 파싱한다`() {
        val click =
            parseNotificationClick(
                mapOf("type" to "REVISIT_REMINDER", "schemaVersion" to "1", "notificationId" to "revisit.123.0"),
                "request",
            )
        assertEquals(NotificationClick("revisit.123.0", NotificationClickTarget.Revisit), click)
    }

    private class Store : RevisitNotificationStore {
        var lastAccess: Long? = null

        override fun lastAccessEpochMillis() = lastAccess

        override fun saveLastAccess(epochMillis: Long) {
            lastAccess = epochMillis
        }
    }

    private class Scheduler : RevisitNotificationScheduler {
        var plan: RevisitNotificationPlan? = null
        var cancellations = 0
        var fail = false
        var firstScheduleGate: CompletableDeferred<Unit>? = null

        override suspend fun cancelOwned() {
            cancellations++
            plan = null
        }

        override suspend fun schedule(plan: RevisitNotificationPlan) {
            firstScheduleGate?.let {
                firstScheduleGate = null
                it.await()
            }
            if (fail) error("Scheduling failed")
            this.plan = plan
        }
    }

    private class Permissions : NotificationPermissionController {
        var status = NotificationAuthorization.AUTHORIZED

        override suspend fun authorization() = status

        override suspend fun requestAuthorization() = Unit

        override suspend fun openSettings() = true
    }
}
