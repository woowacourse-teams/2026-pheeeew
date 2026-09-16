package com.pheeeew.data.remote.report.api

import com.pheeeew.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.data.remote.report.dto.SighReportResultDto

interface SighReportApi {
    suspend fun create(request: SighReportCreateRequestDto): SighReportResultDto
}
