package com.pheeeew.legacy.data.remote.report.api

import com.pheeeew.legacy.data.remote.report.dto.SighReportCreateRequestDto
import com.pheeeew.legacy.data.remote.report.dto.SighReportResultDto

interface SighReportApi {
    suspend fun create(request: SighReportCreateRequestDto): SighReportResultDto
}
