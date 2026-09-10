@file:Suppress("NonAsciiCharacters")

package com.pheeeew.data.repository

import com.pheeeew.data.remote.sigh.api.SighV1Api
import com.pheeeew.data.remote.sigh.api.SighV2Api
import com.pheeeew.data.remote.sigh.dto.PointGeometryDto
import com.pheeeew.data.remote.sigh.dto.SighCreateV1RequestDto
import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighMapResponseDto
import com.pheeeew.data.remote.sigh.dto.SighPageResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV1PropertiesDto
import com.pheeeew.data.remote.sigh.dto.SighV2PropertiesDto
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.SighBounds
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SighRepositoryV2Test {
    private val bounds =
        SighBounds(
            minLongitude = 126.9,
            minLatitude = 37.5,
            maxLongitude = 127.1,
            maxLatitude = 37.6,
        )

    @Test
    fun `첫 페이지 조회를 v2 API에 위임한다`() =
        runTest {
            val api = RecordingSighV2Api()
            val repository = SighRepositoryImpl(UnusedSighV1Api, api)

            val result = repository.getFirstPage(bounds)

            assertEquals(bounds, api.receivedBounds)
            assertEquals(listOf(42L), result.items.map { it.id })
            assertEquals("next-cursor", result.nextCursor)
        }

    @Test
    fun `다음 페이지 조회는 커서를 v2 API에 전달한다`() =
        runTest {
            val api =
                RecordingSighV2Api(
                    nextPageResponse =
                        SighPageResponseDto(
                            items = emptyList(),
                            hasNext = false,
                            nextCursor = null,
                        ),
                )
            val repository = SighRepositoryImpl(UnusedSighV1Api, api)

            val result = repository.getNextPage("current-cursor")

            assertEquals("current-cursor", api.receivedCursor)
            assertTrue(result.items.isEmpty())
            assertNull(result.nextCursor)
        }

    @Test
    fun `식별자로 한숨 상세를 조회한다`() =
        runTest {
            val api = RecordingSighV2Api()
            val repository = SighRepositoryImpl(UnusedSighV1Api, api)

            val result = repository.getById(42L)

            assertEquals(42L, api.receivedId)
            assertEquals("날아가는 고라니", result.nickname)
        }

    @Test
    fun `등록 명령을 v2 요청 DTO로 변환한다`() =
        runTest {
            val api = RecordingSighV2Api()
            val repository = SighRepositoryImpl(UnusedSighV1Api, api)
            val command =
                CreateSighCommand(
                    requestId = "request-123",
                    coordinate = Coordinate(latitude = 37.5665, longitude = 126.9780),
                    memo = "오늘은 조금 지쳤다",
                )

            val result = repository.create(command)

            assertEquals(
                SighCreateV2RequestDto(
                    requestId = "request-123",
                    latitude = 37.5665,
                    longitude = 126.9780,
                    memo = "오늘은 조금 지쳤다",
                ),
                api.receivedCreateRequest,
            )
            assertEquals(42L, result.id)
        }

    private class RecordingSighV2Api(
        private val firstPageResponse: SighPageResponseDto =
            SighPageResponseDto(
                items = listOf(createFeature()),
                hasNext = true,
                nextCursor = "next-cursor",
            ),
        private val nextPageResponse: SighPageResponseDto = firstPageResponse,
        private val detailResponse: SighFeatureDto<SighV2PropertiesDto> = createFeature(),
        private val createResponse: SighFeatureDto<SighV2PropertiesDto> = createFeature(),
    ) : SighV2Api {
        var receivedBounds: SighBounds? = null
            private set
        var receivedCursor: String? = null
            private set
        var receivedId: Long? = null
            private set
        var receivedCreateRequest: SighCreateV2RequestDto? = null
            private set

        override suspend fun getFirstPage(bounds: SighBounds): SighPageResponseDto {
            receivedBounds = bounds
            return firstPageResponse
        }

        override suspend fun getNextPage(cursor: String): SighPageResponseDto {
            receivedCursor = cursor
            return nextPageResponse
        }

        override suspend fun getById(id: Long): SighFeatureDto<SighV2PropertiesDto> {
            receivedId = id
            return detailResponse
        }

        override suspend fun create(request: SighCreateV2RequestDto): SighFeatureDto<SighV2PropertiesDto> {
            receivedCreateRequest = request
            return createResponse
        }
    }

    private companion object {
        fun createFeature() =
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
                        memo = "오늘은 조금 지쳤다",
                        nickname = "날아가는 고라니",
                    ),
            )
    }
}

private object UnusedSighV1Api : SighV1Api {
    override suspend fun getSighs(bounds: SighBounds): SighMapResponseDto = error("사용하지 않는 API입니다.")

    override suspend fun registerSigh(request: SighCreateV1RequestDto): SighFeatureDto<SighV1PropertiesDto> =
        error("사용하지 않는 API입니다.")
}
