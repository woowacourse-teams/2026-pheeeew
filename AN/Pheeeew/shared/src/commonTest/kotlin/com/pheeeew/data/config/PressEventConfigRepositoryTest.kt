package com.pheeeew.data.config

import com.pheeeew.data.repository.PressEventConfigRepository
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

    private class FakeSource(
        var activated: Boolean = false,
    ) : PressEventConfigSource {
        var initializations = 0
        var fetches = 0
        var initializedKey: String? = null
        var fetchInterval: Long? = null
        val readKeys = mutableListOf<String>()
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

        override fun fetchAndActivate(completion: (Boolean) -> Unit) {
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
