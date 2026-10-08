package com.pheeeew.domain.repository.press

import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressSessionState
import kotlinx.coroutines.flow.StateFlow

/** App-session press state and work owner. Calls that mutate state are confined to its owner dispatcher. */
interface PressRepository {
    val state: StateFlow<PressSessionState>

    fun accept(emotion: EmotionState): PressAcceptance

    fun refreshToday(force: Boolean = false)

    fun retryUnsent()

    fun onForeground()
}
