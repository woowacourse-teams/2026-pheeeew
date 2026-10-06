package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModel
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.data.PressDataSource
import com.pheeeew.feature.screens.press.model.PressPeriod
import com.pheeeew.feature.screens.press.model.PressPeriodSnapshots
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class PressUiState(
    val period: PressPeriod = PressPeriod.Today,
    val snapshots: PressPeriodSnapshots,
) {
    val selectedSnapshot
        get() =
            when (period) {
                PressPeriod.Today -> snapshots.today
                PressPeriod.ThisWeek -> snapshots.thisWeek
            }
}

internal class PressViewModel(
    private val dataSource: PressDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PressUiState(snapshots = dataSource.load()))
    val uiState = _uiState.asStateFlow()

    fun onPeriodSelected(period: PressPeriod) {
        _uiState.update { state -> state.copy(period = period) }
    }

    fun onEmotionTap(emotion: EmotionKind): Boolean {
        val snapshots = dataSource.recordPress(emotion)
        _uiState.update { state -> state.copy(snapshots = snapshots) }
        return true
    }
}
