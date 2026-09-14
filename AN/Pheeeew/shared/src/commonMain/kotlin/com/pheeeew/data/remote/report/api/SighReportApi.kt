package com.pheeeew.data.remote.report.api

import com.pheeeew.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.data.remote.report.dto.SighReportResponseDto

interface SighReportApi {
    suspend fun create(request: SighReportCreateRequestDto): SighReportResponseDto
}
