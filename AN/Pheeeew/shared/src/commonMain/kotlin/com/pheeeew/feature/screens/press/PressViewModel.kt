package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.repository.PressRepository
import com.pheeeew.feature.emotion.model.EmotionKind
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns only this screen's projection; durable-for-session data and work live in PressRepository. */
@Inject
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
}
