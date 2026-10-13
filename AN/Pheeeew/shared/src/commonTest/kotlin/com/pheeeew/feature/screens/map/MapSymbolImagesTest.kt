package com.pheeeew.feature.screens.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapSymbolImagesTest {
    @Test
    fun `아직 결과가 없는 키와 최종 실패한 키를 구분한다`() {
        val image = EmotionPinSymbolImage("ready", 1, 1, byteArrayOf(), true)
        val captured = mapOf("ready" to image, "failed" to null)
        val result = captured.symbolImagesFor(setOf("ready", "failed", "pending"))
        assertEquals(listOf(image), result.images)
        assertEquals(setOf("failed"), result.failedKeys)
    }

    @Test
    fun `현재 대상에서 사라진 실패 키는 결과에서 제외하고 재진입 시 준비 중으로 시작한다`() {
        val captured = mutableMapOf<String, EmotionPinSymbolImage?>("failed" to null)
        assertEquals(emptySet(), captured.symbolImagesFor(setOf("next")).failedKeys)
        captured.keys.retainAll(setOf("next"))
        assertEquals(emptySet(), captured.symbolImagesFor(setOf("failed")).failedKeys)
    }

    @Test
    fun `실패한 키에 이미지가 생성되면 실패 결과를 제거한다`() {
        val captured = mutableMapOf<String, EmotionPinSymbolImage?>("key" to null)
        val image = EmotionPinSymbolImage("key", 1, 1, byteArrayOf(), true)
        captured["key"] = image
        assertEquals(MapSymbolImages(listOf(image), emptySet()), captured.symbolImagesFor(setOf("key")))
    }
}
