package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionRegionLevel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import kotlin.test.Test
import kotlin.test.assertEquals

class MapContentRenderStateTest {
    @Test
    fun `핀에서 클러스터로 전환할 때 모든 이미지가 준비될 때까지 기존 화면을 유지한다`() {
        val pin = pin(1)
        val displayed = MapContentRenderState(pins = listOf(pin), pinImages = listOf(image(pin.symbolImageKey())))
        val a = region("a")
        val b = region("b")
        val pending = MapContentRenderState(regions = listOf(a, b), regionLevel = EmotionRegionLevel.SIDO)
        assertEquals(displayed, resolve(pending, displayed))
        assertEquals(displayed, resolve(pending.copy(regionImages = listOf(image(a.symbolImageKey()))), displayed))
        val ready = pending.copy(regionImages = listOf(image(a.symbolImageKey()), image(b.symbolImageKey())))
        assertEquals(ready, resolve(ready, displayed))
    }

    @Test
    fun `클러스터 레벨과 지역 ID가 모두 달라져도 기존 전체 화면을 유지한다`() {
        val old = region("city")
        val displayed =
            MapContentRenderState(
                regions = listOf(old),
                regionLevel = EmotionRegionLevel.SIDO,
                regionImages = listOf(image(old.symbolImageKey())),
            )
        val next = region("district")
        val pending = MapContentRenderState(regions = listOf(next), regionLevel = EmotionRegionLevel.SIGUNGU)
        assertEquals(displayed, resolve(pending, displayed))
        val ready = pending.copy(regionImages = listOf(image(next.symbolImageKey())))
        assertEquals(ready, resolve(ready, displayed))
    }

    @Test
    fun `클러스터에서 핀으로 전환할 때 핀 이미지를 준비한 뒤 교체한다`() {
        val old = region("city")
        val displayed =
            MapContentRenderState(
                regions = listOf(old),
                regionLevel = EmotionRegionLevel.SIDO,
                regionImages = listOf(image(old.symbolImageKey())),
            )
        val pin = pin(1)
        val pending = MapContentRenderState(pins = listOf(pin))
        assertEquals(displayed, resolve(pending, displayed))
        val ready = pending.copy(pinImages = listOf(image(pin.symbolImageKey())))
        assertEquals(ready, resolve(ready, displayed))
    }

    @Test
    fun `정상 빈 응답은 기존 데이터를 제거한다`() {
        val old = region("city")
        val displayed = MapContentRenderState(regions = listOf(old), regionLevel = EmotionRegionLevel.SIDO)
        val emptyRegions = MapContentRenderState(regionLevel = EmotionRegionLevel.SIGUNGU)
        assertEquals(emptyRegions, resolve(emptyRegions, displayed))
        assertEquals(MapContentRenderState(), resolve(MapContentRenderState(), displayed))
    }

    @Test
    fun `전환 대기 중 숨긴 핀은 즉시 표시에서 제외한다`() {
        val pin = pin(1)
        val displayed = MapContentRenderState(pins = listOf(pin), pinImages = listOf(image(pin.symbolImageKey())))
        val pending = MapContentRenderState(regions = listOf(region("city")), regionLevel = EmotionRegionLevel.SIDO)
        assertEquals(emptyList(), resolveMapContentRenderState(pending, displayed, setOf(1)).pins)
    }

    @Test
    fun `최신 후보의 이미지가 없으면 이전 후보 이미지로 전환하지 않는다`() {
        val old = region("city")
        val displayed =
            MapContentRenderState(
                regions = listOf(old),
                regionLevel = EmotionRegionLevel.SIDO,
                regionImages = listOf(image(old.symbolImageKey())),
            )
        val stale = region("district").copy(count = 2)
        val latest = stale.copy(count = 3)
        val pending =
            MapContentRenderState(
                regions = listOf(latest),
                regionLevel = EmotionRegionLevel.SIGUNGU,
                regionImages = listOf(image(stale.symbolImageKey())),
            )
        assertEquals(displayed, resolve(pending, displayed))
    }

    private fun resolve(
        pending: MapContentRenderState,
        displayed: MapContentRenderState,
    ) = resolveMapContentRenderState(pending, displayed, emptySet())

    private fun image(key: String) = EmotionPinSymbolImage(key, 1, 1, byteArrayOf(), true)

    private fun region(id: String) = RegionClusterUiModel(id, id, 127.0, 37.5, 1, null)

    private fun pin(id: Long) =
        EmotionPinUiModel(
            id,
            37.5,
            127.0,
            "2026-10-12T00:00:00Z",
            0.0,
            EmotionTypeUiModel.ANGRY,
            null,
        )
}
