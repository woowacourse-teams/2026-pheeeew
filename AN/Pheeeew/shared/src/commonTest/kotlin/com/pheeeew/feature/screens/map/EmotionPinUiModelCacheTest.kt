package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class EmotionPinUiModelCacheTest {
    @Test
    fun `같은 ID와 표시 데이터의 모델을 재사용하고 표시 필드가 바뀌면 교체한다`() {
        val cache = EmotionPinUiModelCache()
        val pin = pin(7, createdAt = "2026-10-01T00:00:00Z")
        val initial = cache.toUiModel(pin)

        assertSame(initial, cache.toUiModel(pin))

        val moved = cache.toUiModel(pin.copy(latitude = 37.6, rotationDegrees = 24.0))
        assertNotSame(initial, moved)
        assertEquals(37.6, moved.latitude)
        assertEquals(24.0, moved.rotationDegrees)
        assertEquals("2026-10-01T00:00:00Z", initial.createdAt)
    }

    @Test
    fun `날짜와 ID 순서를 유지하며 이전 목록 스냅샷을 변경하지 않는다`() {
        val cache = EmotionPinUiModelCache()
        val pins =
            listOf(
                pin(3, createdAt = "2026-10-02T00:00:00Z"),
                pin(2, createdAt = "2026-10-01T00:00:00Z"),
                pin(1, createdAt = "2026-10-01T00:00:00Z"),
            )
        val first = cache.orderedPins(pins, emptyList(), emptySet(), { true }, { true })

        cache.orderedPins(listOf(pin(4, createdAt = "2026-09-01T00:00:00Z")), emptyList(), emptySet(), { true }, { true })

        assertEquals(listOf(1L, 2L, 3L), first.map(EmotionPinUiModel::id))
        assertEquals(listOf(1L, 2L, 3L), first.map(EmotionPinUiModel::id))
    }

    @Test
    fun `캐시 크기를 설정한 상한 안에 유지한다`() {
        val cache = EmotionPinUiModelCache(maximumEntries = 2)

        cache.toUiModel(pin(1))
        cache.toUiModel(pin(2))
        cache.toUiModel(pin(3))

        assertEquals(2, cache.size)
    }

    private fun pin(
        id: Long,
        latitude: Double = 37.5,
        createdAt: String = "2026-10-01T00:00:00Z",
    ) = EmotionMapPin(
        id = id,
        longitude = 127.0,
        latitude = latitude,
        createdAt = createdAt,
        state = EmotionState.ANGRY,
        rotationDegrees = 0.0,
        groupStamp = null,
    )
}
