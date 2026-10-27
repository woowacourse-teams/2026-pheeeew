package com.pheeeew.feature.screens.press

import com.pheeeew.data.press.race.PressRacePhase
import com.pheeeew.data.press.race.PressRaceRankingState
import com.pheeeew.data.press.race.PressRaceView

internal fun PressRaceRankingState.toNearbyRanking(): PressNearbyRankingUiState {
    val snapshot = cached(PressRaceView.PRESS)
    val fresh = isFresh(PressRaceView.PRESS)
    val status =
        when {
            fresh -> PressNearbyRankingStatus.Ready
            snapshot != null -> PressNearbyRankingStatus.Reconnecting
            session.phase == PressRacePhase.Failed -> PressNearbyRankingStatus.Unavailable
            else -> PressNearbyRankingStatus.Loading
        }
    return PressNearbyRankingUiState(
        rows =
            snapshot?.nearby.orEmpty().mapNotNull { person ->
                val rank = person.rank?.takeIf { it > 0 } ?: return@mapNotNull null
                if (!person.participating || person.score < 0) return@mapNotNull null
                PressNearbyRankRow(
                    rank,
                    person.nickname.orEmpty(),
                    person.score,
                    isMe = snapshot?.me?.publicId?.let { it == person.publicId } == true,
                )
            },
        status = status,
        participationPending = snapshot?.me?.participating == false,
        progress = null,
    )
}

internal fun PressRaceRankingState.toActivity(): PressActivityUiState =
    if (isFresh(PressRaceView.PRESS)) {
        PressActivityUiState(count = requireNotNull(press).activeCount, status = PressActivityStatus.Known)
    } else {
        PressActivityUiState(
            status =
                if (session.phase == PressRacePhase.Connecting || session.phase == PressRacePhase.Initializing) {
                    PressActivityStatus.Loading
                } else {
                    PressActivityStatus.Unknown
                },
        )
    }
