@file:Suppress("NonAsciiCharacters")

package com.pheeeew.legacy.domain.usecase

import com.pheeeew.legacy.data.local.device.DeviceIdStorage
import com.pheeeew.legacy.data.remote.report.api.SighReportApi
import com.pheeeew.legacy.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.legacy.data.remote.report.dto.SighReportResponseDto
import com.pheeeew.legacy.data.remote.report.dto.SighReportResultDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportSighUseCaseTest {
    @Test
    fun `설치별 기기 식별자와 신고 사유를 신고 API에 전달한다`() =
        runTest {
            val api = RecordingSighReportApi()
            val useCase =
                ReportSighUseCase(
                    api = api,
                    deviceIdStorage = FixedDeviceIdStorage,
                )

            useCase(sighId = 42L, reason = "광고성 게시물입니다")

            assertEquals(
                SighReportCreateRequestDto(
                    sighId = 42L,
                    deviceId = "device-id",
                    reason = "광고성 게시물입니다",
                ),
                api.request,
            )
        }

    @Test
    fun `잘못된 한숨 식별자는 신고 요청을 보내지 않는다`() =
        runTest {
            val api = RecordingSighReportApi()
            val useCase =
                ReportSighUseCase(
                    api = api,
                    deviceIdStorage = FixedDeviceIdStorage,
                )

            assertFailsWith<IllegalArgumentException> {
                useCase(sighId = 0L, reason = "신고 사유")
            }
            assertEquals(null, api.request)
        }

    private object FixedDeviceIdStorage : DeviceIdStorage {
        override fun getOrCreate(): String = "device-id"
    }

    private class RecordingSighReportApi : SighReportApi {
        var request: SighReportCreateRequestDto? = null

        override suspend fun create(request: SighReportCreateRequestDto): SighReportResultDto {
            this.request = request
            return SighReportResultDto(
                report =
                    SighReportResponseDto(
                        id = 1L,
                        sighId = request.sighId,
                        reason = request.reason,
                        createdAt = "2026-09-15T00:00:00Z",
                    ),
                isNew = true,
            )
        }
    }
}
