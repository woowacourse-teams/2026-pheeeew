package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.block.EmotionBlockApi
import com.pheeeew.data.remote.block.UserBlockApi
import com.pheeeew.data.remote.emotion.EmotionDeleteApi
import com.pheeeew.data.remote.report.EmotionReportApi
import com.pheeeew.data.repository.EmotionModerationRepositoryImpl
import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import com.pheeeew.domain.usecase.ReportEmotionUseCase

data class EmotionModerationDependencies(
    val block: BlockUserUseCase,
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
    val observed =
        com.pheeeew.feature.monitoring.product
            .MonitoredModeration(repository, apiClient.monitoring)
    return EmotionModerationDependencies(
        block = BlockUserUseCase(observed),
        delete = DeleteEmotionUseCase(observed),
        report = ReportEmotionUseCase(observed),
    )
}
