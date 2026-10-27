package com.pheeeew.data.press.race

/** Connection owner calls these in message order; implementations marshal to their own dispatcher. */
internal interface PressRaceInputObserver {
    suspend fun pending(): List<PressRacePendingInput>

    suspend fun ready(value: PressRaceReady)

    suspend fun result(value: PressRaceInputResult)
}

internal interface PressRaceInputPort {
    val state: kotlinx.coroutines.flow.StateFlow<PressRaceSessionState>

    fun observeInputs(observer: PressRaceInputObserver)

    /** beforeAttempt runs immediately before send, and may refuse an expired/cancelled input. */
    suspend fun sendInput(
        text: String,
        generation: String,
        beforeAttempt: suspend () -> Boolean,
    ): Boolean
}
