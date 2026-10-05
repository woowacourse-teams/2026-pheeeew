package com.pheeeew.feature.screens.map

import kotlin.test.Test
import kotlin.test.assertEquals

class RegionClusterSymbolsTest {
    @Test
    fun `같은 지역은 변경 이미지가 준비될 때까지 기존 핀을 유지한다`() {
        val old = region("a", 1)
        val updated = old.copy(count = 2, longitude = 127.1)
        assertEquals(listOf(old), resolveDrawableRegionClusters(listOf(updated), listOf(old), setOf(old.symbolImageKey())))
        assertEquals(listOf(updated), resolveDrawableRegionClusters(listOf(updated), listOf(old), setOf(updated.symbolImageKey())))
    }

    @Test
    fun `새 응답에 없는 지역은 제거하고 준비된 새 지역만 추가한다`() {
        val old = region("a", 1)
        val added = region("b", 2)
        assertEquals(emptyList(), resolveDrawableRegionClusters(listOf(added), listOf(old), setOf(old.symbolImageKey())))
        assertEquals(listOf(added), resolveDrawableRegionClusters(listOf(added), listOf(old), setOf(added.symbolImageKey())))
        assertEquals(emptyList(), resolveDrawableRegionClusters(emptyList(), listOf(old), setOf(old.symbolImageKey())))
    }

    @Test
    fun `응답 순서가 바뀌어도 지도 핀 순서는 동일하다`() {
        val a = region("a", 1)
        val b = region("b", 2)
        assertEquals(listOf(a, b), resolveDrawableRegionClusters(listOf(b, a), listOf(a, b), setOf(a.symbolImageKey(), b.symbolImageKey())))
    }

    @Test
    fun `교체 대기 중인 이미지도 두 플랫폼에 전달하고 준비 후 교체한다`() {
        val old = region("a", 1)
        val updated = old.copy(count = 2)
        val oldImage = image(old)
        val previous = RegionClusterRenderState(listOf(old), listOf(oldImage))
        assertEquals(previous, resolveRegionClusterRenderState(listOf(updated), emptyList(), previous))
        val newImage = image(updated)
        val replacement = resolveRegionClusterRenderState(listOf(updated), listOf(newImage), previous)
        assertEquals(listOf(updated), replacement.regions)
        assertEquals(listOf(newImage), replacement.images)
        assertEquals(RegionClusterRenderState(), resolveRegionClusterRenderState(emptyList(), emptyList(), replacement))
    }

    private fun image(region: RegionClusterUiModel) = EmotionPinSymbolImage(region.symbolImageKey(), 1, 1, byteArrayOf(), true)
    private fun region(id: String, count: Long) = RegionClusterUiModel(id, id, 127.0, 37.5, count, null)
}
