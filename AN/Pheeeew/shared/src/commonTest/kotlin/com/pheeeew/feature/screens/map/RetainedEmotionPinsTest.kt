package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapBounds
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RetainedEmotionPinsTest {
    private val bounds = EmotionMapBounds(127.0, 37.0, 128.0, 38.0)

    @Test
    fun `상한보다 화면 안의 핀이 많으면 화면 밖의 핀만 제거한다`() {
        val visible = (1L..4L).map { pin(it, 127.5, 37.5) }
        val result = retainEmotionPins(visible + pin(5, 128.1, 37.5), bounds, maximumPins = 3)
        assertEquals(visible, result)
    }

    @Test
    fun `남는 보관 공간은 화면 경계에 가까운 핀부터 채우고 그리기 순서를 유지한다`() {
        val far = pin(1, 130.0, 37.5)
        val close = pin(2, 128.01, 37.5)
        val visible = pin(3, 127.5, 37.5)
        val middle = pin(4, 128.2, 37.5)
        assertEquals(listOf(close, visible), retainEmotionPins(listOf(far, close, visible, middle), bounds, 2))
    }

    @Test
    fun `상한 이내면 화면 밖의 핀과 기존 목록을 그대로 유지한다`() {
        val pins = listOf(pin(1, 127.5, 37.5), pin(2, 128.01, 37.5))
        assertSame(pins, retainEmotionPins(pins, bounds, 2))
    }

    @Test
    fun `이동 후 화면 밖으로 나온 핀은 주변 핀으로 정리할 수 있다`() {
        val pins = (1L..4L).map { pin(it, 127.0 + it / 10.0, 37.5) }
        assertEquals(pins, retainEmotionPins(pins, bounds, 2))
        val movedBounds = EmotionMapBounds(127.35, 37.0, 128.35, 38.0)
        assertEquals(listOf(pins[2], pins[3]), retainEmotionPins(pins, movedBounds, 2))
    }

    @Test
    fun `날짜 변경선을 넘는 화면에서도 화면 안과 가까운 주변 핀을 유지한다`() {
        val wrappedBounds = EmotionMapBounds(179.0, 37.0, -179.0, 38.0)
        val visible = pin(1, -179.5, 37.5)
        val close = pin(2, -178.9, 37.5)
        val far = pin(3, 177.0, 37.5)
        assertEquals(listOf(visible, close), retainEmotionPins(listOf(visible, close, far), wrappedBounds, 2))
    }

    private fun pin(
        id: Long,
        longitude: Double,
        latitude: Double,
    ) = EmotionPinUiModel(id, latitude, longitude, "2026-10-12T00:00:00Z", 0.0, EmotionTypeUiModel.ANGRY, null)
}
