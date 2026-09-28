package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.block.EmotionBlockApi
import com.pheeeew.data.remote.block.UserBlockApi
import com.pheeeew.data.remote.emotion.EmotionDeleteApi
import com.pheeeew.data.remote.report.EmotionReportApi
import com.pheeeew.data.repository.EmotionModerationRepositoryImpl
import com.pheeeew.domain.usecase.BlockEmotionUseCase
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import com.pheeeew.domain.usecase.ReportEmotionUseCase

data class EmotionModerationDependencies(
    val block: BlockEmotionUseCase,
    val delete: DeleteEmotionUseCase,
    val report: ReportEmotionUseCase,
)

fun createEmotionModerationDependencies(apiClient: ApiClient): EmotionModerationDependencies {
    val requests = apiClient.requests
    val repository =
        EmotionModerationRepositoryImpl(
            reportApi = EmotionReportApi(requests),
            emotionBlockApi = EmotionBlockApi(requests),
            userBlockApi = UserBlockApi(requests),
            deleteApi = EmotionDeleteApi(requests),
        )
    return EmotionModerationDependencies(
        block = BlockEmotionUseCase(repository),
        delete = DeleteEmotionUseCase(repository),
        report = ReportEmotionUseCase(repository),
    )
}
