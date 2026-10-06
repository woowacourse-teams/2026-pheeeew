package com.pheeeew.feature.screens.press

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.emotion.component.EmotionFeedbackCatalog
import com.pheeeew.feature.emotion.component.EmotionPad
import com.pheeeew.feature.emotion.component.formatCount
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.data.PressFixtureData
import com.pheeeew.feature.screens.press.model.PressPeriod
import com.pheeeew.feature.screens.press.model.PressPeriodSnapshot
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_fixture_notice
import pheeeew.shared.generated.resources.press_period_this_week
import pheeeew.shared.generated.resources.press_period_today
import pheeeew.shared.generated.resources.press_screen_subtitle
import pheeeew.shared.generated.resources.press_screen_title
import pheeeew.shared.generated.resources.press_summary_all_label
import pheeeew.shared.generated.resources.press_summary_dominant
import pheeeew.shared.generated.resources.press_summary_empty
import pheeeew.shared.generated.resources.press_summary_my_label

@Composable
internal fun PressScreen(
    uiState: PressUiState,
    onPeriodSelected: (PressPeriod) -> Unit,
    onEmotionTap: (EmotionKind) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    val snapshot = uiState.selectedSnapshot
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
        PressPeriodSelector(
            selectedPeriod = uiState.period,
            onSelected = onPeriodSelected,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(18.dp))
        PressSummaryCard(
            period = uiState.period,
            snapshot = snapshot,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(20.dp))
        EmotionPad(
            counts = snapshot.emotionCounts,
            enabled = true,
            onEmotionTap = onEmotionTap,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.press_fixture_notice),
            color = Color(0xFF727872),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = font,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding().height(AppBottomNavigationBarOverlaySpace))
    }
}

@Composable
private fun PressPeriodSelector(
    selectedPeriod: PressPeriod,
    onSelected: (PressPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF1F2EF))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PressPeriod.entries.forEach { period ->
            val isSelected = selectedPeriod == period
            val label =
                stringResource(
                    when (period) {
                        PressPeriod.Today -> Res.string.press_period_today
                        PressPeriod.ThisWeek -> Res.string.press_period_this_week
                    },
                )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AppColors.Primary else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelected(period) }
                        .semantics { selected = isSelected }
                        .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = Color(0xFF202323),
                    fontSize = 15.sp,
                    fontFamily = font,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PressSummaryCard(
    period: PressPeriod,
    snapshot: PressPeriodSnapshot,
    modifier: Modifier = Modifier,
) {
    val font = notoSansKrFontFamily()
    val periodLabel =
        stringResource(
            when (period) {
                PressPeriod.Today -> Res.string.press_period_today
                PressPeriod.ThisWeek -> Res.string.press_period_this_week
            },
        )
    val dominantEmotion = snapshot.mostPressedEmotion
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF7F7F4))
                .border(1.dp, Color(0xFFE7E9E4), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PressSummaryValue(
                label = stringResource(Res.string.press_summary_all_label),
                value = formatCount(snapshot.totalCount),
                modifier = Modifier.weight(1f),
            )
            PressSummaryValue(
                label = stringResource(Res.string.press_summary_my_label),
                value = formatCount(snapshot.myTotalCount),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        val summary =
            if (dominantEmotion == null) {
                stringResource(Res.string.press_summary_empty)
            } else {
                stringResource(
                    Res.string.press_summary_dominant,
                    periodLabel,
                    stringResource(EmotionFeedbackCatalog.name(dominantEmotion)),
                )
            }
        Text(
            text = summary,
            color = Color(0xFF505650),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PressSummaryValue(
    label: String,
    value: String,
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
        Text(
            text = "${value}회",
            color = Color(0xFF202323),
            fontSize = 22.sp,
            fontFamily = font,
            fontWeight = FontWeight.Black,
        )
    }
}

@Preview(name = "프레스", widthDp = 360, heightDp = 800)
@Composable
private fun PressScreenPreview() {
    AppTheme {
        PressScreen(
            uiState = PressUiState(snapshots = PressFixtureData.initialSnapshots()),
            onPeriodSelected = {},
            onEmotionTap = { true },
        )
    }
}
