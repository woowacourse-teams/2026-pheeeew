package com.pheeeew

import com.pheeeew.feature.component.AppDestination
import com.pheeeew.feature.screens.map.detail.EmotionDetailLoadUiModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppMapFeedbackVisibilityTest {
    @Test
    fun `detail failure keeps map feedback visible`() {
        assertTrue(visible(detailState = EmotionDetailLoadUiModel.Failed("오류", canRetry = true)))
    }

    @Test
    fun `feedback remains hidden while detail loads`() {
        assertFalse(visible(detailState = EmotionDetailLoadUiModel.Loading))
    }

    @Test
    fun `closed detail keeps map feedback visible`() {
        assertTrue(visible())
    }

    @Test
    fun `feedback stays scoped to foreground map without modal surfaces`() {
        assertFalse(visible(mainFeaturesReady = false))
        assertFalse(visible(selectedDestination = AppDestination.Group))
        assertFalse(visible(nearbyVisible = true))
        assertFalse(visible(settingsVisible = true))
        assertFalse(visible(hasReportTarget = true))
    }

    private fun visible(
        mainFeaturesReady: Boolean = true,
        selectedDestination: AppDestination = AppDestination.Map,
        nearbyVisible: Boolean = false,
        detailState: EmotionDetailLoadUiModel = EmotionDetailLoadUiModel.Closed,
        settingsVisible: Boolean = false,
        hasReportTarget: Boolean = false,
    ): Boolean =
        shouldShowMapFeedback(
            mainFeaturesReady = mainFeaturesReady,
            selectedDestination = selectedDestination,
            nearbyVisible = nearbyVisible,
            detailState = detailState,
            settingsVisible = settingsVisible,
            hasReportTarget = hasReportTarget,
        )
}
