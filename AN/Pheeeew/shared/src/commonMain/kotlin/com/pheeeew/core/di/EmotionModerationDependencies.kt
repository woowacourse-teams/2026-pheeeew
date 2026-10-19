package com.pheeeew.core.di

import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import com.pheeeew.domain.usecase.ReportEmotionUseCase

data class EmotionModerationDependencies(
    val block: BlockUserUseCase,
    val delete: DeleteEmotionUseCase,
    val report: ReportEmotionUseCase,
)
