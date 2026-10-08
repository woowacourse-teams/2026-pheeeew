package com.pheeeew.feature.screens.press

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
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
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_count_unavailable
import pheeeew.shared.generated.resources.press_screen_subtitle
import pheeeew.shared.generated.resources.press_screen_title
import pheeeew.shared.generated.resources.press_summary_all_label
import pheeeew.shared.generated.resources.press_summary_my_label
import pheeeew.shared.generated.resources.press_summary_title

@Composable
internal fun PressScreen(
    uiState: PressUiState,
    onEmotionTap: (EmotionKind) -> Boolean,
    modifier: Modifier = Modifier,
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
                .background(Color.White)
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
            Spacer(Modifier.height(12.dp))
            val allCount =
                uiState.allToday?.total?.let { serverTotal ->
                    if (serverTotal <= Long.MAX_VALUE - uiState.optimisticAllPressCount) {
                        serverTotal + uiState.optimisticAllPressCount
                    } else {
                        null
                    }
                }
            PressSummaryCard(
                allCount = allCount,
                myCount = uiState.myToday?.total?.plus(uiState.optimisticMyTotalCount),
                isLoadingAll = uiState.isLoadingAll,
                isLoadingMy = uiState.isLoadingMy,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
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

@Composable
private fun PressSummaryCard(
    allCount: Long?,
    myCount: Long?,
    isLoadingAll: Boolean,
    isLoadingMy: Boolean,
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.Gray50)
                .border(1.dp, AppColors.BorderLight, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.press_summary_title),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(thickness = 1.dp, color = AppColors.BorderLight)

        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val numberWidth =
                (constraints.maxWidth - with(density) { 1.dp.roundToPx() }) / 2 -
                    textMeasurer
                        .measure(
                            "회",
                            style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        ).size.width - with(density) { 2.dp.roundToPx() }
            val numbers = listOfNotNull(allCount, myCount).map(::formatCount)
            val numberFontSize =
                listOf(22, 20, 18, 16, 14, 12)
                    .firstOrNull { size ->
                        numbers.all { number ->
                            textMeasurer
                                .measure(
                                    number,
                                    style =
                                        TextStyle(
                                            fontFamily = font,
                                            fontWeight = FontWeight.Black,
                                            fontSize = size.sp,
                                        ),
                                ).size.width <= numberWidth
                        }
                    }?.sp ?: 12.sp

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PressSummaryValue(
                    label = stringResource(Res.string.press_summary_all_label),
                    value = allCount,
                    isLoading = isLoadingAll,
                    numberFontSize = numberFontSize,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(thickness = 1.dp, color = AppColors.BorderLight, modifier = Modifier.height(30.dp))
                PressSummaryValue(
                    label = stringResource(Res.string.press_summary_my_label),
                    value = myCount,
                    isLoading = isLoadingMy,
                    numberFontSize = numberFontSize,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PressSummaryValue(
    label: String,
    value: Long?,
    isLoading: Boolean,
    numberFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = font,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text =
                    value?.let(::formatCount)
                        ?: if (isLoading) "…" else stringResource(Res.string.press_count_unavailable),
                color = AppColors.TextPrimary,
                fontSize = numberFontSize,
                fontFamily = font,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (value != null) {
                Text(
                    text = "회",
                    color = Color(0xFF505650),
                    fontSize = 13.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
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
        )
    }
}
