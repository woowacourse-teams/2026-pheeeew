package com.pheeeew.feature.screens.press

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import pheeeew.shared.generated.resources.press_emotion_counts_today_title
import pheeeew.shared.generated.resources.press_input_location_preparing
import pheeeew.shared.generated.resources.press_input_location_unavailable
import pheeeew.shared.generated.resources.press_input_outcome_unknown
import pheeeew.shared.generated.resources.press_input_queue_full
import pheeeew.shared.generated.resources.press_input_rejected
import pheeeew.shared.generated.resources.press_pending_count
import pheeeew.shared.generated.resources.press_retry
import pheeeew.shared.generated.resources.press_retry_location
import pheeeew.shared.generated.resources.press_screen_subtitle
import pheeeew.shared.generated.resources.press_screen_title
import pheeeew.shared.generated.resources.press_statistics_failed
import pheeeew.shared.generated.resources.press_summary_all_label
import pheeeew.shared.generated.resources.press_summary_my_label
import pheeeew.shared.generated.resources.press_summary_title

@Composable
internal fun PressScreen(
    uiState: PressUiState,
    onEmotionTap: (EmotionKind) -> Boolean,
    onRetryStatistics: () -> Unit,
    onRetryLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    val emotionCounts =
        EmotionKind.entries.map { kind ->
            EmotionCountUiModel(kind, uiState.myEmotionCounts[kind] ?: 0L)
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(Res.string.press_screen_title),
                color = Color(0xFF202323),
                fontSize = 28.sp,
                fontFamily = font,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.press_screen_subtitle),
                color = Color(0xFF777C78),
                fontSize = 15.sp,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(24.dp))
        PressSummaryCard(
            allCount = uiState.allToday?.total,
            myCount = uiState.myToday?.total,
            isLoadingAll = uiState.isLoadingAll,
            isLoadingMy = uiState.isLoadingMy,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (uiState.hasAllError || uiState.hasMyError) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.press_statistics_failed),
                    color = Color(0xFF777C78),
                    fontSize = 12.sp,
                    fontFamily = font,
                )
                TextButton(onClick = onRetryStatistics) {
                    Text(stringResource(Res.string.press_retry), fontFamily = font)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.press_emotion_counts_today_title),
            color = Color(0xFF505650),
            fontSize = 14.sp,
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(10.dp))
        EmotionPad(
            counts = emotionCounts,
            countPlaceholder =
                if (uiState.myToday ==
                    null
                ) {
                    stringResource(Res.string.press_count_unavailable)
                } else {
                    null
                },
            enabled = true,
            onEmotionTap = onEmotionTap,
            arrangement = EmotionPadArrangement.TwoThree,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        uiState.notice?.let { notice ->
            val message =
                when (notice) {
                    PressNotice.LocationPreparing -> stringResource(Res.string.press_input_location_preparing)
                    PressNotice.LocationUnavailable -> stringResource(Res.string.press_input_location_unavailable)
                    PressNotice.QueueFull -> stringResource(Res.string.press_input_queue_full)
                    PressNotice.Rejected -> stringResource(Res.string.press_input_rejected)
                    PressNotice.OutcomeUnknown -> stringResource(Res.string.press_input_outcome_unknown)
                }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = message,
                    color = Color(0xFF777C78),
                    fontSize = 12.sp,
                    fontFamily = font,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
                if (notice == PressNotice.LocationUnavailable) {
                    TextButton(onClick = onRetryLocation) {
                        Text(stringResource(Res.string.press_retry_location), fontFamily = font)
                    }
                }
            }
        }
        if (uiState.pendingPressCount > 0 || uiState.isSending) {
            Text(
                text = stringResource(Res.string.press_pending_count, uiState.pendingPressCount),
                color = Color(0xFF777C78),
                fontSize = 12.sp,
                fontFamily = font,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF7F7F4))
                .border(1.dp, Color(0xFFE7E9E4), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.press_summary_title),
            color = Color(0xFF505650),
            fontSize = 14.sp,
            fontFamily = font,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE4E7E2)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressSummaryValue(
                label = stringResource(Res.string.press_summary_all_label),
                value = allCount,
                isLoading = isLoadingAll,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(42.dp)
                    .background(Color(0xFFE4E7E2)),
            )
            PressSummaryValue(
                label = stringResource(Res.string.press_summary_my_label),
                value = myCount,
                isLoading = isLoadingMy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PressSummaryValue(
    label: String,
    value: Long?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFF777C78),
            fontSize = 12.sp,
            fontFamily = font,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text =
                    value?.let(::formatCount)
                        ?: if (isLoading) "…" else stringResource(Res.string.press_count_unavailable),
                color = Color(0xFF202323),
                fontSize = 26.sp,
                fontFamily = font,
                fontWeight = FontWeight.Black,
            )
            if (value != null) {
                Text(
                    text = "회",
                    color = Color(0xFF505650),
                    fontSize = 13.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 3.dp),
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
                    allToday = AllDailyPressSnapshot(pressDate = "2026-10-08", total = 82),
                    isLoadingMy = false,
                    isLoadingAll = false,
                ),
            onEmotionTap = { true },
            onRetryStatistics = {},
            onRetryLocation = {},
        )
    }
}
