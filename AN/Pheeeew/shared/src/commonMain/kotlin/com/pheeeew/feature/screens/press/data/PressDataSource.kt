package com.pheeeew.feature.screens.press.data

import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.model.PressPeriodSnapshots

internal interface PressDataSource {
    fun load(): PressPeriodSnapshots

    fun recordPress(emotion: EmotionKind): PressPeriodSnapshots
}
