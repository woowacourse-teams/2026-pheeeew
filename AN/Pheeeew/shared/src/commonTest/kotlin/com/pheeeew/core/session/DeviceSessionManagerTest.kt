package com.pheeeew.core.session

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.SessionAccessException
import com.pheeeew.domain.model.device.DeviceAccess
import com.pheeeew.domain.model.device.DeviceSessionFailure
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.repository.DeviceSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceSessionManagerTest {
    @Test
    fun `concurrent callers share success and cancellation does not cancel registration`() =
        runTest {
            var calls = 0
            val gate = CompletableDeferred<Unit>()
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult {
                            calls++
                            gate.await()
                            return ready()
                        }
                    },
                    backgroundScope,
                ) { 1000 }
            val first = async { manager.prepare() }
            val second = async { manager.prepare() }
            runCurrent()
            first.cancel()
            gate.complete(Unit)
            assertIs<DeviceSessionResult.Ready>(second.await())
            assertEquals(1, calls)
            manager.accessToken()
            assertEquals(1, calls)
        }

    @Test
    fun `concurrent failed callers share failure rather than serial retries`() =
        runTest {
            var calls = 0
            val gate = CompletableDeferred<Unit>()
            val failure = DeviceSessionResult.Failed(DeviceSessionFailure(DeviceSessionFailureKind.NETWORK))
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult {
                            calls++
                            gate.await()
                            return failure
                        }
                    },
                    backgroundScope,
                ) { 1000 }
            val callers = List(10) { async { manager.prepare() } }
            runCurrent()
            gate.complete(Unit)
            callers.forEach { assertSame(failure, it.await()) }
            assertSame(failure, manager.prepare())
            assertEquals(1, calls)
        }

    @Test
    fun `stale 401 reuses newer token and never invalidates it`() =
        runTest {
            var calls = 0
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult {
                            calls++
                            return ready(if (calls == 1) "A" else "B")
                        }
                    },
                    backgroundScope,
                ) { 1000 }
            val tokenA = manager.accessToken()
            assertEquals("B", manager.recover(tokenA).value)
            assertEquals("B", manager.recover(tokenA).value)
            assertEquals(2, calls)
        }

    @Test
    fun `new identity during refresh blocks replay of previous identity request`() =
        runTest {
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult = ready("B", 2)
                    },
                    backgroundScope,
                ) { 1000 }
            val error = assertFailsWith<SessionAccessException> { manager.recover(AccessToken("A", 1)) }
            assertEquals("SESSION_CHANGED", error.details.reason)
            assertEquals(2, manager.accessToken().generation)
        }

    @Test
    fun `short ttl is usable and refreshes at its bounded margin`() =
        runTest {
            var now = 1000L
            var calls = 0
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult {
                            calls++
                            return DeviceSessionResult.Ready(DeviceAccess("A", now, now + 1000, 1))
                        }
                    },
                    backgroundScope,
                ) { now }
            manager.accessToken()
            now += 800
            manager.accessToken()
            assertEquals(1, calls)
            now += 100
            manager.accessToken()
            assertEquals(2, calls)
        }

    @Test
    fun `DEVICE-004는 세션을 재등록 대기로 바꾸고 늦은 응답은 새 기기를 지우지 않는다`() =
        runTest {
            var generation = 1L
            var invalidations = 0
            val repository =
                object : DeviceSessionRepository {
                    override suspend fun prepare(): DeviceSessionResult = ready("token-$generation", generation)

                    override suspend fun invalidateMissingDevice(generation: Long): DeviceSessionResult {
                        invalidations++
                        return DeviceSessionResult.NeedsRegistration()
                    }
                }
            val manager = DeviceSessionManager(repository, backgroundScope) { 1000 }
            val rejected = manager.accessToken()
            manager.deviceUnavailable(rejected)
            assertIs<DeviceSessionResult.NeedsRegistration>(manager.state.value)
            assertEquals(1, invalidations)
            generation = 2
            manager.register("new nickname")
            manager.deviceUnavailable(rejected)
            assertEquals(1, invalidations)
            assertEquals(2L, manager.accessToken().generation)
        }

    @Test
    fun `DEVICE-004 arriving during refresh must invalidate after that refresh finishes`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            var invalidations = 0
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult {
                            calls++
                            if (calls > 1) gate.await()
                            return ready(if (calls == 1) "A" else "B")
                        }

                        override suspend fun invalidateMissingDevice(generation: Long): DeviceSessionResult {
                            invalidations++
                            return DeviceSessionResult.NeedsRegistration()
                        }
                    },
                    backgroundScope,
                ) { 1000 }
            val rejected = manager.accessToken()
            val refreshing = async { manager.recover(rejected) }
            runCurrent()
            val missing = async { manager.deviceUnavailable(rejected) }
            runCurrent()
            gate.complete(Unit)
            refreshing.await()
            missing.await()
            assertEquals(1, invalidations)
            assertEquals(2, calls)
            assertIs<DeviceSessionResult.NeedsRegistration>(manager.state.value)
        }

    @Test
    fun `DEVICE-004 applies to the same device even after its access token changes`() =
        runTest {
            var calls = 0
            var invalidations = 0
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult = ready("token-${++calls}")

                        override suspend fun invalidateMissingDevice(generation: Long): DeviceSessionResult {
                            invalidations++
                            return DeviceSessionResult.NeedsRegistration()
                        }
                    },
                    backgroundScope,
                ) { 1000 }
            val rejected = manager.accessToken()
            manager.recover(rejected)
            manager.deviceUnavailable(rejected)
            assertEquals(1, invalidations)
            assertIs<DeviceSessionResult.NeedsRegistration>(manager.state.value)
        }

    @Test
    fun `DEVICE-004 waiting for a new registration does not invalidate the new device`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var invalidations = 0
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult = DeviceSessionResult.NeedsRegistration()

                        override suspend fun register(nickname: String): DeviceSessionResult {
                            gate.await()
                            return ready("new-device", 2)
                        }

                        override suspend fun invalidateMissingDevice(generation: Long): DeviceSessionResult {
                            invalidations++
                            return DeviceSessionResult.NeedsRegistration()
                        }
                    },
                    backgroundScope,
                ) { 1000 }
            val registering = async { manager.register("nickname") }
            runCurrent()
            val missing = async { manager.deviceUnavailable(AccessToken("old-device", 1)) }
            runCurrent()
            gate.complete(Unit)
            registering.await()
            missing.await()
            assertEquals(0, invalidations)
            assertEquals(2L, manager.accessToken().generation)
        }

    @Test
    fun `cancelling the HTTP caller does not drop invalidation waiting for refresh`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            var invalidations = 0
            val manager =
                DeviceSessionManager(
                    object : DeviceSessionRepository {
                        override suspend fun prepare(): DeviceSessionResult {
                            calls++
                            if (calls > 1) gate.await()
                            return ready()
                        }

                        override suspend fun invalidateMissingDevice(generation: Long): DeviceSessionResult {
                            invalidations++
                            return DeviceSessionResult.NeedsRegistration()
                        }
                    },
                    backgroundScope,
                ) { 1000 }
            val rejected = manager.accessToken()
            val refreshing = async { manager.recover(rejected) }
            runCurrent()
            val missing = async { manager.deviceUnavailable(rejected) }
            runCurrent()
            missing.cancel()
            gate.complete(Unit)
            refreshing.await()
            runCurrent()
            assertEquals(1, invalidations)
            assertIs<DeviceSessionResult.NeedsRegistration>(manager.state.value)
        }

    private fun ready(
        value: String = "A",
        generation: Long = 1,
    ) = DeviceSessionResult.Ready(DeviceAccess(value, 1000, 1_801_000, generation))
}
