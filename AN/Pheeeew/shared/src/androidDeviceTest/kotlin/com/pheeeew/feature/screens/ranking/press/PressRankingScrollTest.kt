package com.pheeeew.feature.screens.ranking.press

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import kotlin.test.assertNotNull

class PressRankingScrollTest {
    @Test
    fun missingPersonalRecordDoesNotHideParticipantsOnShortScreenWithLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val activity =
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ComponentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                            Box(Modifier.size(320.dp, 400.dp)) {
                                PressRankingScreen(
                                    onBack = {},
                                    uiState = PressRankingUiState(status = PressRankingStatus.Ready),
                                    onRetry = {},
                                    onRefresh = {},
                                    rows = listOf(PressRaceRowModel(1, "스크롤확인참가자", 10, 1f)),
                                    historical = true,
                                )
                            }
                        }
                    }
                }
            }
            assertNotNull(device.wait(Until.findObject(By.textContains("기록이 없어요")), 5_000))
            val target = By.descContains("스크롤확인참가자")
            repeat(12) {
                if (device.hasObject(target)) return@repeat
                val bounds = checkNotNull(device.findObject(By.scrollable(true))).visibleBounds
                device.swipe(bounds.centerX(), bounds.bottom - 10, bounds.centerX(), bounds.top + 10, 30)
                device.waitForIdle()
            }
            assertNotNull(
                device.wait(Until.findObject(target), 5_000),
                "Participant must remain reachable by scrolling",
            )
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
