package com.pheeeew.feature.screens.report.adapter

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.report.EmotionReportApi
import com.pheeeew.data.remote.report.EmotionReportCreateRequestDto
import com.pheeeew.feature.screens.report.ReportAction
import com.pheeeew.feature.screens.report.ReportResult

class ApiReportAction(
    private val api: EmotionReportApi,
) : ReportAction {
    override suspend fun report(
        emotionId: Long,
        reason: String,
    ): ReportResult =
        when (val result = api.create(EmotionReportCreateRequestDto(emotionId, reason))) {
            is ApiResult.Success -> ReportResult.Reported
            is ApiResult.Failure -> result.reason.toReportResult()
        }

    private fun NetworkFailure.toReportResult(): ReportResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                if (error?.code == OWN_EMOTION_ERROR_CODE) ReportResult.OwnEmotion else ReportResult.Failed
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            is NetworkFailure.Transport,
            is NetworkFailure.Unexpected,
            is NetworkFailure.Contract,
            -> {
                ReportResult.Failed
            }
        }

    private companion object {
        const val OWN_EMOTION_ERROR_CODE = "REPORT-002"
    }
}
