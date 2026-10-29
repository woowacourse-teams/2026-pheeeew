package com.pheeeew.data.repository

import com.pheeeew.data.remote.api.ReportApi
import com.pheeeew.data.remote.dto.EmotionReportCreateRequestDto
import com.pheeeew.data.remote.mapper.toModerationResult
import com.pheeeew.domain.repository.ReportRepository
import dev.zacsweers.metro.Inject

@Inject
class ReportRepositoryImpl(
    private val api: ReportApi,
) : ReportRepository {
    override suspend fun report(
        emotionId: Long,
        reason: String,
    ) = api.create(EmotionReportCreateRequestDto(emotionId, reason)).toModerationResult()
}
