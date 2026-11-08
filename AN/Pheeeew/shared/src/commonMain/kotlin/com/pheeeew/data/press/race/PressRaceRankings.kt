package com.pheeeew.data.press.race

import com.pheeeew.core.di.device.PressRaceReceiveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Read-only screen projections borrow the app session; they never own its socket. */
internal interface PressRaceRankings {
    val state: StateFlow<PressRaceRankingState>

    fun refresh(view: PressRaceView)
}

internal data class PressRaceRankingState(
    val session: PressRaceSessionState = PressRaceSessionState(),
    val press: PressRaceSnapshot? = null,
    val all: PressRaceSnapshot? = null,
    val comparison: PressRaceComparisonState? = null,
    val comparisonPress: PressRankingSnapshot? = null,
    val comparisonAll: PressRankingSnapshot? = null,
) {
    fun cached(view: PressRaceView): PressRaceSnapshot? = if (view == PressRaceView.PRESS) press else all

    fun ranking(view: PressRaceView): PressRankingSnapshot? =
        if (view == PressRaceView.PRESS) {
            comparisonPress ?: press?.toRankingSnapshot()
        } else {
            comparisonAll ?: all?.toRankingSnapshot()
        }

    fun isFresh(view: PressRaceView): Boolean =
        (comparison?.takeIf { it.phase == PressRacePhase.Ready && it.view == view }?.snapshot?.view == view) ||
            session.currentSnapshot?.view == view

    fun update(next: PressRaceSessionState): PressRaceRankingState {
        val current = next.currentSnapshot
        val ready = next.ready
        val currentGenerationDate =
            next.snapshot
                ?.takeIf {
                    it.serverGeneration == ready?.serverGeneration &&
                        it.connectionGeneration == ready?.connectionGeneration
                }?.date
        val date = listOfNotNull(current?.date, ready?.date, currentGenerationDate, next.dateFloor).maxOrNull()
        val retainedPress = press?.takeIf { date == null || it.date == date }
        val retainedAll = all?.takeIf { date == null || it.date == date }
        return copy(
            session = next,
            press = if (current?.view == PressRaceView.PRESS) current else retainedPress,
            all = if (current?.view == PressRaceView.ALL) current else retainedAll,
            comparison = null,
            comparisonPress = null,
            comparisonAll = null,
        )
    }

    fun updateComparison(next: PressRaceComparisonState): PressRaceRankingState {
        val snapshot = next.snapshot
        val date = listOfNotNull(snapshot?.date, session.dateFloor).maxOrNull()
        val currentPress = if (next.view == PressRaceView.PRESS) snapshot else null
        val currentAll = if (next.view == PressRaceView.ALL) snapshot else null
        val retainedPress = comparisonPress?.takeIf { date == null || it.date == date }
        val retainedAll = comparisonAll?.takeIf { date == null || it.date == date }
        val phase = next.phase
        return copy(
            session =
                PressRaceSessionState(
                    phase = phase,
                    view = next.view,
                    isAwaitingSnapshot = phase == PressRacePhase.Initializing,
                    failure =
                        if (phase == PressRacePhase.Failed) {
                            PressRaceFailure(
                                PressRaceFailureKind.Transport,
                                httpStatus = next.failureStatus,
                                code = next.failureKind,
                            )
                        } else {
                            null
                        },
                    isRecovering = next.isRecovering,
                    dateFloor = date,
                    notice = null,
                ),
            comparison = next,
            comparisonPress = currentPress ?: retainedPress,
            comparisonAll = currentAll ?: retainedAll,
            // A build has one receive mode; never retain WS cache alongside comparison results.
            press = null,
            all = null,
        )
    }
}

internal fun PressRaceSnapshot.toRankingSnapshot() =
    PressRankingSnapshot(date, generatedAt, activeCount, view, me, nearby, top100)

internal class PressRaceRankingStore(
    sessionState: StateFlow<PressRaceSessionState>,
    private val requestRefresh: (PressRaceView) -> Unit,
    scope: CoroutineScope,
    comparisonReceiver: PressRaceComparisonReceiver? = null,
) : PressRaceRankings {
    private val mutableState = MutableStateFlow(PressRaceRankingState().update(sessionState.value))
    override val state = mutableState.asStateFlow()

    init {
        val activeComparison = comparisonReceiver?.takeIf { it.selectedMode != PressRaceReceiveMode.WEBSOCKET }
        if (activeComparison != null) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                combine(sessionState, activeComparison.state) { _, comparison -> comparison }
                    .collect { mutableState.value = mutableState.value.updateComparison(it) }
            }
        } else {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                sessionState.collect { mutableState.value = mutableState.value.update(it) }
            }
        }
    }

    override fun refresh(view: PressRaceView) = requestRefresh(view)
}
