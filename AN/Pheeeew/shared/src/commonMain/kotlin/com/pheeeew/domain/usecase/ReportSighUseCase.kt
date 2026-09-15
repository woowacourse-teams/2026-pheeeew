package com.pheeeew.domain.usecase

import com.pheeeew.data.local.device.DeviceIdStorage
import com.pheeeew.data.remote.report.api.SighReportApi
import com.pheeeew.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.data.remote.report.dto.SighReportResponseDto

class ReportSighUseCase(
    private val api: SighReportApi,
    private val deviceIdStorage: DeviceIdStorage,
) {
    suspend operator fun invoke(
        sighId: Long,
        reason: String,
    ): SighReportResponseDto {
        require(sighId > 0) { "한숨 식별자는 양수여야 합니다." }
        require(reason.isNotBlank()) { "신고 사유는 비어 있을 수 없습니다." }

        return api.create(
            SighReportCreateRequestDto(
                sighId = sighId,
                deviceId = deviceIdStorage.getOrCreate(),
                reason = reason,
            ),
        )
    }
}
