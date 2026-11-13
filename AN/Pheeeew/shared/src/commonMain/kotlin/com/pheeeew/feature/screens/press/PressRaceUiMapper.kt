package com.pheeeew.feature.screens.press

import com.pheeeew.data.press.race.PressRacePhase
import com.pheeeew.data.press.race.PressRaceRankingState
import com.pheeeew.data.press.race.PressRaceView

internal fun PressRaceRankingState.toNearbyRanking(): PressNearbyRankingUiState {
    val snapshot = cached(PressRaceView.PRESS)
    val fresh = isFresh(PressRaceView.PRESS)
    val recovering = session.isRecovering || session.phase == PressRacePhase.Failed
    val status =
        when {
            fresh -> PressNearbyRankingStatus.Ready
            snapshot != null && recovering -> PressNearbyRankingStatus.Reconnecting
            snapshot != null -> PressNearbyRankingStatus.Cached
            recovering -> PressNearbyRankingStatus.Unavailable
            else -> PressNearbyRankingStatus.Loading
        }
    return PressNearbyRankingUiState(
        rows =
            snapshot?.nearby.orEmpty().mapNotNull { person ->
                val publicId = person.publicId ?: return@mapNotNull null
                val rank = person.rank?.takeIf { it > 0 } ?: return@mapNotNull null
                if (!person.participating || person.score < 0) return@mapNotNull null
                PressNearbyRankRow(
                    rank,
                    person.nickname.orEmpty(),
                    person.score,
                    isMe = snapshot?.me?.publicId?.let { it == person.publicId } == true,
                    publicId = publicId,
                )
            },
        status = status,
        participationPending = snapshot?.me?.participating == false,
        progress = null,
        date = snapshot?.date,
    )
}

internal fun PressRaceRankingState.toActivity(): PressActivityUiState {
    val snapshot = cached(PressRaceView.PRESS)
    return when {
        session.isRecovering -> {
            PressActivityUiState(status = PressActivityStatus.Unknown)
        }

        isFresh(PressRaceView.PRESS) -> {
            PressActivityUiState(
                count = requireNotNull(snapshot).activeCount,
                status = PressActivityStatus.Known,
                notice =
                    session.notice?.let {
                        PressRaceNotice(it.id, it.entered, it.passed, it.passedBy)
                    },
            )
        }

        snapshot != null && session.phase != PressRacePhase.Failed && session.phase != PressRacePhase.Closed -> {
            PressActivityUiState(count = snapshot.activeCount, status = PressActivityStatus.Cached)
        }

        session.phase == PressRacePhase.Connecting || session.phase == PressRacePhase.Initializing -> {
            PressActivityUiState(status = PressActivityStatus.Loading)
        }

        else -> {
            PressActivityUiState(status = PressActivityStatus.Unknown)
        }
    }
}
