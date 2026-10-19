package com.pheeeew.data.remote.mapper

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.domain.repository.EmotionModerationResult

internal fun ApiResult<*>.toModerationResult(): EmotionModerationResult =
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
