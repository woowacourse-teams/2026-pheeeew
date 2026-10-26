package com.pheeeew.feature.screens.press

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.AllDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.emotion.component.EmotionPad
import com.pheeeew.feature.emotion.component.EmotionPadArrangement
import com.pheeeew.feature.emotion.component.formatCount
import com.pheeeew.feature.emotion.model.EmotionCountUiModel
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.component.PressNearbyRankingCard
import com.pheeeew.feature.screens.press.component.PressRankingEntry
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_count_unavailable
import pheeeew.shared.generated.resources.press_screen_subtitle
import pheeeew.shared.generated.resources.press_screen_title

@Composable
internal fun PressScreen(
    uiState: PressUiState,
    onEmotionTap: (EmotionKind) -> Boolean,
    onOpenRanking: () -> Unit,
    modifier: Modifier = Modifier,
    nearbyRanking: PressNearbyRankingUiState = PressNearbyRankingUiState(status = PressNearbyRankingStatus.Unavailable),
    activity: PressActivityUiState? = null,
    faceVisuals: Map<EmotionKind, EmotionFaceVisual> = emptyMap(),
) {
    val font = notoSansKrFontFamily()
    val emotionCounts =
        EmotionKind.entries.map { kind ->
            EmotionCountUiModel(kind, uiState.myEmotionCounts[kind] ?: 0L)
        }
    val unavailableCount = stringResource(Res.string.press_count_unavailable)
    val countTextOverrides =
        if (uiState.myToday == null && !uiState.isLoadingMy) {
            EmotionKind.entries.associateWith { kind ->
                uiState.optimisticPressCounts[kind]?.let { "+${formatCount(it)}" } ?: unavailableCount
            }
        } else {
            emptyMap()
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.GroupBackground)
                .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(28.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(
                    text = stringResource(Res.string.press_screen_title),
                    color = AppColors.TextPrimary,
                    fontSize = 24.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.press_screen_subtitle),
                    color = Color(0xFF777C78),
                    fontSize = 14.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Medium,
                )
            }
            PressRankingEntry(
                onClick = onOpenRanking,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 24.dp),
            )
            PressNearbyRankingCard(nearbyRanking, Modifier.padding(horizontal = 24.dp), activity = activity)
            Spacer(Modifier.height(20.dp))
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val padWidth =
                ((maxHeight.value / 345f * 354f + 32f).dp)
                    .coerceIn(200.dp.coerceAtMost(maxWidth), maxWidth)
            EmotionPad(
                counts = emotionCounts,
                faceVisuals = faceVisuals,
                optimisticPressCounts = uiState.optimisticPressCounts,
                countTextOverrides = countTextOverrides,
                enabled = true,
                onEmotionTap = onEmotionTap,
                arrangement = EmotionPadArrangement.TwoThree,
                modifier = Modifier.width(padWidth).padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding().height(AppBottomNavigationBarOverlaySpace))
    }
}

@Preview(name = "프레스", widthDp = 360, heightDp = 800)
@Composable
private fun PressScreenPreview() {
    AppTheme {
        PressScreen(
            uiState =
                PressUiState(
                    myToday =
                        MyDailyPressSnapshot(
                            pressDate = "2026-10-08",
                            counts =
                                mapOf(
                                    EmotionState.FRUSTRATED to 1,
                                    EmotionState.IRRITATED to 2,
                                    EmotionState.EXHAUSTED to 3,
                                    EmotionState.DISCOURAGED to 0,
                                    EmotionState.ANGRY to 1,
                                ),
                            total = 7,
                        ),
                    allToday = AllDailyPressSnapshot(pressDate = "2026-10-08", total = 100_000_000),
                    isLoadingMy = false,
                    isLoadingAll = false,
                ),
            onEmotionTap = { true },
            onOpenRanking = {},
        )
    }
}
