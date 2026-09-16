@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.remote.sigh.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SighPageResponseDtoTest {
    @Test
    fun `페이지 응답을 SighPage로 변환한다`() {
        val response =
            SighPageResponseDto(
                items = listOf(createFeature()),
                hasNext = true,
                nextCursor = "opaque-cursor",
            )

        val result = response.toSighPage()

        assertEquals(listOf(42L), result.items.map { it.id })
        assertEquals("opaque-cursor", result.nextCursor)
        assertTrue(result.hasNext)
    }

    @Test
    fun `다음 페이지 여부와 커서가 일치하지 않으면 변환에 실패한다`() {
        val response =
            SighPageResponseDto(
                items = emptyList(),
                hasNext = true,
                nextCursor = null,
            )

        assertFailsWith<IllegalArgumentException> {
            response.toSighPage()
        }
    }

    private fun createFeature() =
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
                    memo = null,
                    nickname = "날아가는 고라니",
                ),
        )
}
