package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.emotion.component.rememberTapReducedMotion
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.race_angry_a
import pheeeew.shared.generated.resources.race_angry_b
import pheeeew.shared.generated.resources.race_annoyed_a
import pheeeew.shared.generated.resources.race_annoyed_b
import pheeeew.shared.generated.resources.race_blocked_a
import pheeeew.shared.generated.resources.race_blocked_b
import pheeeew.shared.generated.resources.race_defeated_a
import pheeeew.shared.generated.resources.race_defeated_b
import pheeeew.shared.generated.resources.race_tired_a
import pheeeew.shared.generated.resources.race_tired_b

/** Missing representative emotions use the defeated runner as a visual fallback. */
internal fun runningRaceResource(
    emotion: EmotionKind?,
    secondPose: Boolean,
): DrawableResource =
    when (emotion) {
        EmotionKind.Angry -> if (secondPose) Res.drawable.race_angry_b else Res.drawable.race_angry_a
        EmotionKind.Annoyed -> if (secondPose) Res.drawable.race_annoyed_b else Res.drawable.race_annoyed_a
        EmotionKind.Blocked -> if (secondPose) Res.drawable.race_blocked_b else Res.drawable.race_blocked_a
        EmotionKind.Defeated, null -> if (secondPose) Res.drawable.race_defeated_b else Res.drawable.race_defeated_a
        EmotionKind.Tired -> if (secondPose) Res.drawable.race_tired_b else Res.drawable.race_tired_a
    }

private data class RunningPose(
    val secondImage: Boolean,
    val bounce: Float,
    val lean: Float,
)

private val stillPose = RunningPose(secondImage = false, bounce = 0f, lean = 0f)

/** Uses the shared screen clock only while its image intersects the ranking viewport. */
@Composable
internal fun RunningRaceCharacter(
    emotion: EmotionKind?,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    phaseSeed: Int = 0,
) {
    val clock = LocalRaceAnimationClock.current
    var visible by remember(clock) { mutableStateOf(false) }
    DisposableEffect(clock, visible) {
        if (visible) clock?.registerVisible(1)
        onDispose { if (visible) clock?.registerVisible(-1) }
    }
    val stopped = reducedMotion || rememberTapReducedMotion() || clock == null
    val phase =
        remember(clock, phaseSeed) {
            derivedStateOf {
                val seed = phaseSeed.and(Int.MAX_VALUE)
                val cycleMillis = 300L + seed.mod(181)
                val offsetMillis = seed.toLong().mod(cycleMillis)
                val position = ((clock?.elapsedMillis?.longValue ?: 0L) + offsetMillis).mod(cycleMillis)
                when ((position * 4L / cycleMillis).toInt()) {
                    0 -> RunningPose(secondImage = false, bounce = 0f, lean = -2.2f)
                    1 -> RunningPose(secondImage = false, bounce = -1.5f, lean = 1.4f)
                    2 -> RunningPose(secondImage = true, bounce = 0f, lean = 2.2f)
                    else -> RunningPose(secondImage = true, bounce = -1.5f, lean = -1.4f)
                }
            }
        }
    val pose = if (!stopped && visible) phase.value else stillPose
    val resource = runningRaceResource(emotion, pose.secondImage)
    val visibilityModifier =
        if (clock == null) {
            modifier
        } else {
            modifier.onGloballyPositioned { coordinates ->
                val next = raceCharacterVisible(coordinates.boundsInWindow(), clock.viewport.value)
                if (visible != next) visible = next
            }
        }
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier =
            visibilityModifier.graphicsLayer {
                translationY = if (stopped || !visible) 0f else pose.bounce.dp.toPx()
                rotationZ = if (stopped || !visible) 0f else pose.lean
            },
    )
}
