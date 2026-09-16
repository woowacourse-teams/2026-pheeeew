@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.sigh.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class SighFeatureDtoTest {
    @Test
    fun `v1 Feature를 지도 핀으로 변환한다`() {
        val feature =
            SighFeatureDto(
                type = "Feature",
                id = 42L,
                geometry =
                    PointGeometryDto(
                        type = "Point",
                        coordinates = listOf(126.9780, 37.5665),
                    ),
                properties = SighV1PropertiesDto(),
            )

        val result = feature.toSighPin()

        assertEquals(42L, result.id)
        assertEquals(37.5665, result.coordinate.latitude)
        assertEquals(126.9780, result.coordinate.longitude)
    }

    @Test
    fun `v2 Feature를 Sigh로 변환한다`() {
        val feature = createV2Feature(memo = null)

        val result = feature.toSigh()

        assertEquals(42L, result.id)
        assertEquals(37.5669, result.coordinate.latitude)
        assertEquals(126.9774, result.coordinate.longitude)
        assertEquals(Instant.parse("2026-09-01T12:00:00Z"), result.createdAt)
        assertNull(result.memo)
        assertEquals("날아가는 고라니", result.nickname)
        assertEquals(result.id, result.toPin().id)
        assertEquals(result.coordinate, result.toPin().coordinate)
        assertEquals(result.createdAt, result.toPin().createdAt)
    }

    @Test
    fun `v1 Feature의 createdAt을 지도 핀으로 전달한다`() {
        val feature =
            SighFeatureDto(
                type = "Feature",
                id = 42L,
                geometry =
                    PointGeometryDto(
                        type = "Point",
                        coordinates = listOf(126.9780, 37.5665),
                    ),
                properties = SighV1PropertiesDto(createdAt = "2026-09-01T12:00:00Z"),
            )

        assertEquals(Instant.parse("2026-09-01T12:00:00Z"), feature.toSighPin().createdAt)
    }

    @Test
    fun `v1 Feature의 잘못된 createdAt은 null로 변환한다`() {
        val feature =
            SighFeatureDto(
                type = "Feature",
                id = 42L,
                geometry =
                    PointGeometryDto(
                        type = "Point",
                        coordinates = listOf(126.9780, 37.5665),
                    ),
                properties = SighV1PropertiesDto(createdAt = "not-an-instant"),
            )

        assertNull(feature.toSighPin().createdAt)
    }

    @Test
    fun `Point 좌표가 2개 미만이면 변환에 실패한다`() {
        val feature =
            createV2Feature().copy(
                geometry =
                    PointGeometryDto(
                        type = "Point",
                        coordinates = listOf(126.9774),
                    ),
            )

        assertFailsWith<IllegalArgumentException> {
            feature.toSigh()
        }
    }

    private fun createV2Feature(memo: String? = "오늘은 조금 지쳤다") =
        SighFeatureDto(
            type = "Feature",
            id = 42L,
            geometry =
                PointGeometryDto(
                    type = "Point",
                    coordinates = listOf(126.9774, 37.5669),
                ),
            properties =
                SighV2PropertiesDto(
                    createdAt = "2026-09-01T12:00:00Z",
                    memo = memo,
                    nickname = "날아가는 고라니",
                ),
        )
}
