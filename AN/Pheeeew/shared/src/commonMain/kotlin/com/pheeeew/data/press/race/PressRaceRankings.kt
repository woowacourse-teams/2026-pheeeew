package com.pheeeew.data.press.race

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    fun cached(view: PressRaceView): PressRaceSnapshot? = if (view == PressRaceView.PRESS) press else all

    fun isFresh(view: PressRaceView): Boolean = session.currentSnapshot?.view == view

    fun update(next: PressRaceSessionState): PressRaceRankingState {
        val current = next.currentSnapshot
        val ready = next.ready
        val currentGenerationDate =
            next.snapshot
                ?.takeIf {
                    it.serverGeneration == ready?.serverGeneration &&
                        it.connectionGeneration == ready?.connectionGeneration
                }?.date
        val date = current?.date ?: listOfNotNull(ready?.date, currentGenerationDate).maxOrNull()
        val retainedPress = press?.takeIf { date == null || it.date == date }
        val retainedAll = all?.takeIf { date == null || it.date == date }
        return copy(
            session = next,
            press = if (current?.view == PressRaceView.PRESS) current else retainedPress,
            all = if (current?.view == PressRaceView.ALL) current else retainedAll,
        )
    }
}

internal class PressRaceRankingStore(
    sessionState: StateFlow<PressRaceSessionState>,
    private val requestRefresh: (PressRaceView) -> Unit,
    scope: CoroutineScope,
) : PressRaceRankings {
    private val mutableState = MutableStateFlow(PressRaceRankingState().update(sessionState.value))
    override val state = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionState.collect { mutableState.value = mutableState.value.update(it) }
        }
    }

    override fun refresh(view: PressRaceView) = requestRefresh(view)
}
