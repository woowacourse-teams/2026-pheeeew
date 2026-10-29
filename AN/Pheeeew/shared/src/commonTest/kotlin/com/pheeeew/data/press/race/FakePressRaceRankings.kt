package com.pheeeew.data.press.race

import kotlinx.coroutines.flow.MutableStateFlow

internal class FakePressRaceRankings : PressRaceRankings {
    override val state = MutableStateFlow(PressRaceRankingState())
    val refreshed = mutableListOf<PressRaceView>()

    override fun refresh(view: PressRaceView) {
        refreshed += view
    }
}
