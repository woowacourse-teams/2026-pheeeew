package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.block.EmotionBlockApi
import com.pheeeew.data.remote.block.UserBlockApi
import com.pheeeew.data.remote.report.EmotionReportApi
import com.pheeeew.data.remote.report.EmotionReportCreateRequestDto
import com.pheeeew.domain.repository.EmotionModerationRepository
import com.pheeeew.domain.repository.EmotionModerationResult

class EmotionModerationRepositoryImpl(
    private val reportApi: EmotionReportApi,
    private val emotionBlockApi: EmotionBlockApi,
    private val userBlockApi: UserBlockApi,
) : EmotionModerationRepository {
    override suspend fun report(
        emotionId: Long,
        reason: String,
    ): EmotionModerationResult = reportApi.create(EmotionReportCreateRequestDto(emotionId, reason)).toModerationResult()

    override suspend fun blockEmotion(emotionId: Long): EmotionModerationResult =
        emotionBlockApi.create(emotionId).toModerationResult()

    override suspend fun blockUser(emotionId: Long): EmotionModerationResult =
        userBlockApi.create(emotionId).toModerationResult()

    private fun ApiResult<*>.toModerationResult(): EmotionModerationResult =
        when (this) {
            is ApiResult.Success -> EmotionModerationResult.Success
            is ApiResult.Failure -> reason.toModerationResult()
        }

    private fun NetworkFailure.toModerationResult(): EmotionModerationResult =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                when {
                    error?.code == "REPORT-002" || error?.code == "BLOCK-002" -> EmotionModerationResult.OwnEmotion
                    error?.code == "BLOCK-003" -> EmotionModerationResult.AuthorUnknown
                    statusCode == 404 -> EmotionModerationResult.NotFound
                    else -> EmotionModerationResult.Unavailable
                }
            }

            is NetworkFailure.Transport -> {
                EmotionModerationResult.NetworkUnavailable
            }

            else -> {
                EmotionModerationResult.Unavailable
            }
        }
}
