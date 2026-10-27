package com.pheeeew.feature.screens.ranking.press

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.data.press.race.PressRaceParticipant
import com.pheeeew.data.press.race.PressRacePhase
import com.pheeeew.data.press.race.PressRaceRankingState
import com.pheeeew.data.press.race.PressRaceRankings
import com.pheeeew.data.press.race.PressRaceView
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PressRankingUiState(
    val date: String? = null,
    val people: List<PressPersonRank> = emptyList(),
    val me: PressPersonRank? = null,
    val status: PressRankingStatus = PressRankingStatus.Loading,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val hasRefreshError: Boolean = false,
)

internal enum class PressRankingStatus { Loading, Ready, Failed }

@Inject
internal class PressRankingViewModel(
    private val rankings: PressRaceRankings,
    monitoring: Monitoring = NoOpMonitoring,
) : ViewModel() {
    val telemetry = ProductMonitoring(monitoring, "ranking")
    private val mutableUiState = MutableStateFlow(rankings.state.value.toRankingUiState())
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            rankings.state.collect { mutableUiState.value = it.toRankingUiState() }
        }
    }

    fun onRetry() = rankings.refresh(PressRaceView.ALL)

    fun onRefresh() = rankings.refresh(PressRaceView.ALL)
}

internal fun PressRaceRankingState.toRankingUiState(): PressRankingUiState {
    val snapshot = cached(PressRaceView.ALL)
    val fresh = isFresh(PressRaceView.ALL)
    val failed = session.phase == PressRacePhase.Failed
    return PressRankingUiState(
        date = snapshot?.date,
        people = snapshot?.top100.orEmpty().mapNotNull { it.toPersonRank(snapshot?.me?.publicId) },
        me = snapshot?.me?.toPersonRank(snapshot.me.publicId),
        status =
            when {
                snapshot != null -> PressRankingStatus.Ready
                failed -> PressRankingStatus.Failed
                else -> PressRankingStatus.Loading
            },
        isRefreshing =
            snapshot != null && !fresh && session.view == PressRaceView.ALL &&
                session.phase in setOf(PressRacePhase.Ready, PressRacePhase.Connecting, PressRacePhase.Initializing),
        isStale = snapshot != null && !fresh,
        hasRefreshError = snapshot != null && failed,
    )
}

private fun PressRaceParticipant.toPersonRank(meId: String?): PressPersonRank? {
    val id = publicId?.takeIf { it.isNotBlank() } ?: return null
    val place = rank?.takeIf { it > 0 } ?: return null
    if (!participating || score < 0) return null
    return PressPersonRank(place, nickname.orEmpty(), score, id, meId != null && id == meId)
}
