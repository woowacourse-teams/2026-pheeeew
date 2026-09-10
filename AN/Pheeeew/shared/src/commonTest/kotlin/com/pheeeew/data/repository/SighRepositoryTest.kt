@file:Suppress("NonAsciiCharacters", "ktlint:standard:multiline-expression-wrapping")

package com.pheeeew.data.repository

import com.pheeeew.data.remote.sigh.api.SighV1Api
import com.pheeeew.data.remote.sigh.api.SighV2Api
import com.pheeeew.data.remote.sigh.dto.PointGeometryDto
import com.pheeeew.data.remote.sigh.dto.SighCreateV1RequestDto
import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighMapResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV1PropertiesDto
import com.pheeeew.data.remote.sigh.dto.SighV2PropertiesDto
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SighRepositoryTest {
    private val bounds =
        SighBounds(
            minLongitude = 126.9,
            minLatitude = 37.5,
            maxLongitude = 127.1,
            maxLatitude = 37.6,
        )

    @Test
    fun `조회 응답의 Feature들을 SighPin 목록으로 변환한다`() =
        runTest {
            val api =
                RecordingSighV1Api(
                    getSighsResponse =
                        SighMapResponseDto(
                            type = "FeatureCollection",
                            truncated = false,
                            features =
                                listOf(
                                    SighFeatureDto(
                                        type = "Feature",
                                        id = 42L,
                                        geometry =
                                            PointGeometryDto(
                                                type = "Point",
                                                coordinates = listOf(126.9780, 37.5665),
                                            ),
                                        properties = SighV1PropertiesDto(),
                                    ),
                                ),
                        ),
                )
            val repository = SighRepositoryImpl(api, UnusedSighV2Api)

            val result = repository.getMapSighs(bounds)

            assertEquals(
                listOf(
                    SighPin(
                        id = 42L,
                        coordinate =
                            Coordinate(
                                latitude = 37.5665,
                                longitude = 126.9780,
                            ),
                    ),
                ),
                result,
            )
            assertEquals(bounds, api.receivedBounds)
        }

    private class RecordingSighV1Api(
        private val getSighsResponse: SighMapResponseDto =
            SighMapResponseDto(
                type = "FeatureCollection",
                truncated = false,
                features = emptyList(),
            ),
    ) : SighV1Api {
        var receivedBounds: SighBounds? = null
            private set

        override suspend fun getSighs(bounds: SighBounds): SighMapResponseDto {
            receivedBounds = bounds
            return getSighsResponse
        }

        override suspend fun registerSigh(request: SighCreateV1RequestDto): SighFeatureDto<SighV1PropertiesDto> =
            error("사용하지 않는 API입니다.")
    }

    private object UnusedSighV2Api : SighV2Api {
        override suspend fun getFirstPage(bounds: SighBounds) = error("사용하지 않는 API입니다.")

        override suspend fun getNextPage(cursor: String) = error("사용하지 않는 API입니다.")

        override suspend fun getById(id: Long) = error("사용하지 않는 API입니다.")

        override suspend fun create(request: SighCreateV2RequestDto): SighFeatureDto<SighV2PropertiesDto> =
            error("사용하지 않는 API입니다.")
    }
}
