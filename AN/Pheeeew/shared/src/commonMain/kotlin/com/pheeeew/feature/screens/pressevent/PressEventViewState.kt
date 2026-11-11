package com.pheeeew.feature.screens.pressevent

import com.pheeeew.domain.model.pressevent.PressEventAttemptResult
import com.pheeeew.domain.model.pressevent.PressEventMyRank
import com.pheeeew.feature.screens.ranking.press.PressPersonRank

internal data class PressEventViewState(
    val game: PressEventUiState = PressEventUiState(),
    val configured: Boolean = false,
    val leader: PressPersonRank? = null,
    val people: List<PressPersonRank> = emptyList(),
    val me: PressEventMyRank? = null,
    val rankedAt: String? = null,
    val nextCursor: String? = null,
    val rankingLoading: Boolean = false,
    val rankingError: Boolean = false,
    val loadingMore: Boolean = false,
    val nextPageError: Boolean = false,
    val submission: EventSubmissionStatus = EventSubmissionStatus.None,
    val result: PressEventAttemptResult? = null,
) {
    val canStart: Boolean get() =
        configured && submission != EventSubmissionStatus.Submitting && !submission.canRetrySubmission
}
