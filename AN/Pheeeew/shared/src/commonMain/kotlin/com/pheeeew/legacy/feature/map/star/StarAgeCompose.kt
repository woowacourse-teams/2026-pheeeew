package com.pheeeew.legacy.feature.map.star

import androidx.compose.ui.graphics.Color
import com.pheeeew.legacy.core.designsystem.theme.AppColors

/** Compose에서 별 생애 단계를 렌더링 색상으로 변환합니다. */
internal fun StarAgeStage.toComposeStarColor(): Color =
    when (this) {
        StarAgeStage.Fresh -> AppColors.StarFresh
        StarAgeStage.Warm -> AppColors.StarWarm
        StarAgeStage.Deep -> AppColors.StarDeep
        StarAgeStage.Unknown -> AppColors.StarUnknown
    }
