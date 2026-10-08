package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.model.press.PressSessionState
import com.pheeeew.domain.repository.press.PressRepository
import com.pheeeew.feature.emotion.model.EmotionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun EmotionState.toUiKind(): EmotionKind =
    when (this) {
        EmotionState.FRUSTRATED -> EmotionKind.Blocked
        EmotionState.IRRITATED -> EmotionKind.Annoyed
        EmotionState.EXHAUSTED -> EmotionKind.Tired
        EmotionState.DISCOURAGED -> EmotionKind.Defeated
        EmotionState.ANGRY -> EmotionKind.Angry
    }

internal data class PressUiState(
    val myToday: MyDailyPressSnapshot? = null,
    val allToday: AllDailyPressSnapshot? = null,
    val isLoadingMy: Boolean = true,
    val isLoadingAll: Boolean = true,
    val isSending: Boolean = false,
    val pendingPressCount: Long = 0L,
    val optimisticPressCounts: Map<EmotionKind, Long> = emptyMap(),
    val optimisticAllPressCount: Long = 0L,
) {
    val optimisticMyTotalCount: Long
        get() = optimisticPressCounts.values.sum()

    val myEmotionCounts: Map<EmotionKind, Long>
        get() =
            myToday?.counts?.mapKeys { (emotion, _) -> emotion.toUiKind() }.orEmpty()
}

/** Owns only this screen's projection; durable-for-session data and work live in PressRepository. */
internal class PressViewModel(
    private val repository: PressRepository,
) : ViewModel() {
    // Project the session cache synchronously so a recreated screen never flashes its empty default state.
    private val mutableUiState = MutableStateFlow(repository.state.value.toUiState())
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { sessionState ->
                mutableUiState.value = sessionState.toUiState()
            }
        }
    }

    fun onScreenResumed() {
        repository.refreshToday()
        repository.retryUnsent()
    }

    fun retryUnsent() = repository.retryUnsent()

    fun onEmotionTap(emotion: EmotionKind): Boolean =
        repository.accept(emotion.toDomainState()) == PressAcceptance.Accepted

    private fun PressSessionState.toUiState(): PressUiState =
        PressUiState(
            myToday = myToday,
            allToday = allToday,
            isLoadingMy = isLoadingMy,
            isLoadingAll = isLoadingAll,
            isSending = isSending,
            pendingPressCount = pendingPressCount,
            optimisticPressCounts = optimisticCounts.mapKeys { (emotion, _) -> emotion.toUiKind() },
            optimisticAllPressCount = optimisticAllPressCount,
        )

    private fun EmotionKind.toDomainState(): EmotionState =
        when (this) {
            EmotionKind.Blocked -> EmotionState.FRUSTRATED
            EmotionKind.Annoyed -> EmotionState.IRRITATED
            EmotionKind.Tired -> EmotionState.EXHAUSTED
            EmotionKind.Defeated -> EmotionState.DISCOURAGED
            EmotionKind.Angry -> EmotionState.ANGRY
        }
}
