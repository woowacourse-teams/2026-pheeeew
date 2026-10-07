package com.pheeeew.feature.screens.press

import androidx.lifecycle.ViewModel
import com.pheeeew.feature.screens.press.data.InMemoryPressDataSource
import com.pheeeew.feature.screens.press.data.PressDataSource

/** Keeps fixture-only press counts for the app session across route and Activity recreation. */
internal class PressFixtureSessionViewModel : ViewModel() {
    val dataSource: PressDataSource = InMemoryPressDataSource()
}
