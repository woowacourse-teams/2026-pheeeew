package com.pheeeew.data.config

import com.pheeeew.data.repository.PressEventConfigRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressEventConfigRepositoryTest {
    @Test
    fun `환경별 키와 fetch 간격을 사용한다`() {
        val dev = PressEventConfigRepository("dev", true)
        val prod = PressEventConfigRepository("prod", false)
        assertEquals("press_event_enabled_dev", dev.parameterKey)
        assertEquals("press_event_enabled_prod", prod.parameterKey)
        assertEquals(0L, dev.minimumFetchIntervalSeconds)
        assertEquals(3_600L, prod.minimumFetchIntervalSeconds)
        assertFailsWith<IllegalStateException> { PressEventConfigRepository("unknown", true) }
    }

    @Test
    fun `첫 실행 오프라인이면 기본값 false를 유지한다`() {
        val repository = PressEventConfigRepository("dev", true)
        val source = FakeSource()
        assertFalse(repository.eventButtonEnabled.value)
        repository.onForeground(source)
        source.finishInitialization()
        source.finishFetch(succeeded = false)
        assertFalse(repository.eventButtonEnabled.value)
    }

    @Test
    fun `마지막 활성화 캐시는 네트워크 응답 전에 복원하고 실패해도 유지한다`() {
        val source = FakeSource(activated = true)
        val repository = PressEventConfigRepository("prod", false)
        repository.onForeground(source)
        source.finishInitialization()
        assertTrue(repository.eventButtonEnabled.value)
        assertEquals("press_event_enabled_prod", source.initializedKey)
        assertEquals(3_600L, source.fetchInterval)
        source.finishFetch(succeeded = false)
        assertTrue(repository.eventButtonEnabled.value)
        // A new app owner restores the SDK's same persisted active value, even while offline.
        val restarted = PressEventConfigRepository("prod", false)
        restarted.onForeground(source)
        source.finishInitialization()
        source.finishFetch(succeeded = false)
        assertTrue(restarted.eventButtonEnabled.value)
    }

    @Test
    fun `같은 앱에서 ON OFF ON을 적용하고 선택한 환경의 키만 읽는다`() {
        val source = FakeSource()
        val repository = PressEventConfigRepository("dev", true)
        repository.onForeground(source)
        source.finishInitialization()
        listOf(true, false, true).forEachIndexed { index, enabled ->
            if (index > 0) repository.onForeground(source)
            source.activated = enabled
            source.finishFetch(succeeded = true)
            assertEquals(enabled, repository.eventButtonEnabled.value)
        }
        assertEquals(1, source.initializations)
        assertTrue(source.readKeys.all { it == "press_event_enabled_dev" })
    }

    @Test
    fun `초기화와 조회 중 전경 호출을 합치고 완료 후 다음 조회를 허용한다`() {
        val source = FakeSource()
        val repository = PressEventConfigRepository("dev", true)
        repeat(3) { repository.onForeground(source) }
        assertEquals(1, source.initializations)
        source.finishInitialization()
        repeat(3) { repository.onForeground(source) }
        assertEquals(1, source.fetches)
        source.finishFetch(succeeded = false)
        repository.onForeground(source)
        assertEquals(2, source.fetches)
    }

    @Test
    fun `초기화 실패 후 다음 전경 복귀에서 다시 초기화한다`() {
        val source = FakeSource(activated = true)
        val repository = PressEventConfigRepository("dev", true)
        repository.onForeground(source)
        source.finishInitialization(succeeded = false)
        assertFalse(repository.eventButtonEnabled.value)
        assertEquals(0, source.fetches)
        repository.onForeground(source)
        source.finishInitialization()
        assertEquals(2, source.initializations)
        assertTrue(repository.eventButtonEnabled.value)
    }

    @Test
    fun `조회 실패는 마지막 OFF 값도 보존하고 이후 성공으로 복구한다`() {
        val source = FakeSource()
        val repository = PressEventConfigRepository("dev", true)
        repository.onForeground(source)
        source.finishInitialization()
        source.finishFetch(succeeded = true)
        repository.onForeground(source)
        source.activated = true
        source.finishFetch(succeeded = false)
        assertFalse(repository.eventButtonEnabled.value)
        repository.onForeground(source)
        source.finishFetch(succeeded = true)
        assertTrue(repository.eventButtonEnabled.value)
    }

    @Test
    fun `진입 확인은 캐시 true 대신 강제 조회한 false를 반환한다`() =
        runTest {
            val source = FakeSource(activated = true)
            val repository = PressEventConfigRepository("prod", false)
            repository.onForeground(source)
            source.finishInitialization()
            source.finishFetch(succeeded = true)
            val entry = async(start = CoroutineStart.UNDISPATCHED) { repository.fetchEventEnabledForEntry() }
            assertFalse(entry.isCompleted)
            source.activated = false
            source.finishFetch(succeeded = true)
            assertEquals(false, entry.await())
            assertFalse(repository.eventButtonEnabled.value)
            assertEquals(listOf(false, true), source.forcedFetches)
        }

    @Test
    fun `전경 갱신 중 진입하면 완료를 기다린 뒤 별도로 강제 조회한다`() =
        runTest {
            val source = FakeSource(activated = true)
            val repository = PressEventConfigRepository("prod", false)
            repository.onForeground(source)
            source.finishInitialization()
            val entry = async(start = CoroutineStart.UNDISPATCHED) { repository.fetchEventEnabledForEntry() }
            assertEquals(1, source.fetches)
            source.finishFetch(succeeded = true)
            assertEquals(2, source.fetches)
            assertFalse(entry.isCompleted)
            source.activated = false
            source.finishFetch(succeeded = true)
            assertEquals(false, entry.await())
        }

    @Test
    fun `진입 확인 실패는 캐시를 보존하되 이전 true로 진입을 허용하지 않는다`() =
        runTest {
            val source = FakeSource(activated = true)
            val repository = PressEventConfigRepository("dev", true)
            repository.onForeground(source)
            source.finishInitialization()
            source.finishFetch(succeeded = true)
            val entry = async(start = CoroutineStart.UNDISPATCHED) { repository.fetchEventEnabledForEntry() }
            source.finishFetch(succeeded = false)
            assertEquals(null, entry.await())
            assertTrue(repository.eventButtonEnabled.value)
        }

    @Test
    fun `진입 확인 대기자 취소는 공유 SDK 요청과 다른 대기자를 취소하지 않는다`() =
        runTest {
            val source = FakeSource(activated = true)
            val repository = PressEventConfigRepository("dev", true)
            repository.onForeground(source)
            source.finishInitialization()
            source.finishFetch(succeeded = true)
            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.fetchEventEnabledForEntry() }
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.fetchEventEnabledForEntry() }
            first.cancel()
            source.finishFetch(succeeded = true)
            assertEquals(true, second.await())
            assertEquals(2, source.fetches)
        }

    private class FakeSource(
        var activated: Boolean = false,
    ) : PressEventConfigSource {
        var initializations = 0
        var fetches = 0
        var initializedKey: String? = null
        var fetchInterval: Long? = null
        val readKeys = mutableListOf<String>()
        val forcedFetches = mutableListOf<Boolean>()
        private var initialization: ((Boolean) -> Unit)? = null
        private var fetch: ((Boolean) -> Unit)? = null

        override fun initialize(
            parameterKey: String,
            minimumFetchIntervalSeconds: Long,
            completion: (Boolean) -> Unit,
        ) {
            initializations++
            initializedKey = parameterKey
            fetchInterval = minimumFetchIntervalSeconds
            initialization = completion
        }

        override fun activatedBoolean(parameterKey: String): Boolean {
            readKeys += parameterKey
            return activated
        }

        override fun fetchAndActivate(
            forceRefresh: Boolean,
            completion: (Boolean) -> Unit,
        ) {
            forcedFetches += forceRefresh
            fetches++
            fetch = completion
        }

        fun finishInitialization(succeeded: Boolean = true) {
            val callback = checkNotNull(initialization)
            initialization = null
            callback(succeeded)
        }

        fun finishFetch(succeeded: Boolean) {
            val callback = checkNotNull(fetch)
            fetch = null
            callback(succeeded)
        }
    }
}
