package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressGroupRank
import com.pheeeew.feature.screens.ranking.press.samplePressGroupRanks
import kotlinx.coroutines.delay

private const val GROUP_CARD_INTERVAL_MILLIS = 2_500L
private const val GROUP_CARD_FADE_MILLIS = 800

@Composable
internal fun MyPressRankList(
    groups: List<PressGroupRank>,
    emotion: PressEmotion,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return

    var currentIndex by remember(groups) { mutableIntStateOf(0) }
    var rotationResetKey by remember(groups) { mutableIntStateOf(0) }

    fun showNextGroup() {
        if (groups.size > 1) {
            currentIndex = (currentIndex + 1) % groups.size
            rotationResetKey++
        }
    }

    LaunchedEffect(groups, rotationResetKey) {
        if (groups.size > 1) {
            while (true) {
                delay(GROUP_CARD_INTERVAL_MILLIS)
                currentIndex = (currentIndex + 1) % groups.size
            }
        }
    }

    AnimatedContent(
        targetState = groups[currentIndex],
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(GROUP_CARD_FADE_MILLIS)) togetherWith
                    fadeOut(animationSpec = tween(GROUP_CARD_FADE_MILLIS))
            ).using(SizeTransform(clip = false))
        },
        label = "myPressGroupRank",
    ) { group ->
        MyPressRankCard(
            group = group,
            emotion = emotion,
            onClick = { showNextGroup() },
        )
    }
}

@Preview(name = "내 프레스 그룹 랭킹")
@Composable
private fun MyPressRankListPreview() {
    AppTheme {
        MyPressRankList(
            groups = samplePressGroupRanks.filter { it.isMyGroup },
            emotion = PressEmotion.Frustrated,
        )
    }
}
