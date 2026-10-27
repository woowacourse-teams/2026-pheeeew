package com.pheeeew.feature.screens.press.measurement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.data.press.race.PressRaceFrameKey

/** Records the first visible Compose draw pass, not OS presentation or another device's display. */
@Composable
internal fun Modifier.pressRaceDrawMeasurement(
    key: PressRaceFrameKey?,
    onDraw: (PressRaceFrameKey) -> Unit,
): Modifier {
    val drawn = remember { LastDraw() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    return drawWithContent {
        drawContent()
        if (key != null && key != drawn.key && focused && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            drawn.key = key
            onDraw(key)
        }
    }
}

private class LastDraw {
    var key: PressRaceFrameKey? = null
}
