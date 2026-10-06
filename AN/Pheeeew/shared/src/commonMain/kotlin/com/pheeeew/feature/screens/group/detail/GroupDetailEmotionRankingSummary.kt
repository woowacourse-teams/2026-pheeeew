package com.pheeeew.feature.screens.group.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.detail.component.formatCount
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_emotion_rank_label
import pheeeew.shared.generated.resources.group_detail_emotion_rank_loading
import pheeeew.shared.generated.resources.group_detail_emotion_rank_no_presses
import pheeeew.shared.generated.resources.group_detail_emotion_rank_retry
import pheeeew.shared.generated.resources.group_detail_emotion_rank_unavailable
import pheeeew.shared.generated.resources.group_detail_emotion_rank_weekly_count
import pheeeew.shared.generated.resources.group_detail_rank_empty
import pheeeew.shared.generated.resources.group_detail_rank_number
import pheeeew.shared.generated.resources.group_detail_rank_refresh_error
import pheeeew.shared.generated.resources.group_detail_rank_refreshing

@Composable
internal fun RowScope.GroupDetailEmotionRankingSummary(
    state: GroupDetailEmotionRankingUiState,
    weeklyPressCount: GroupDetailWeeklyPressCountUiState,
    pendingPressCount: Long,
    onRetry: () -> Unit,
) {
    val label = stringResource(Res.string.group_detail_emotion_rank_label)
    val value = state.content.displayValue()
    val supportingValues = state.supportingValues(weeklyPressCount, pendingPressCount)
    val accessibilityDescription = (listOf(label, value) + supportingValues).joinToString(", ")

    GroupDetailSummaryValue(
        label = label,
        value = value,
        supportingValues = supportingValues,
        modifier =
            Modifier
                .weight(1f)
                .then(
                    if (state.canRetry(weeklyPressCount)) {
                        Modifier
                            .clickable(role = Role.Button, onClick = onRetry)
                            .semantics { contentDescription = accessibilityDescription }
                    } else {
                        Modifier.semantics { contentDescription = accessibilityDescription }
                    },
                ),
    )
}

@Composable
private fun GroupDetailEmotionRankingContent.displayValue(): String =
    when (this) {
        GroupDetailEmotionRankingContent.Loading -> {
            stringResource(Res.string.group_detail_emotion_rank_loading)
        }

        is GroupDetailEmotionRankingContent.Ranked -> {
            stringResource(Res.string.group_detail_rank_number, rank)
        }

        GroupDetailEmotionRankingContent.NoPresses -> {
            stringResource(Res.string.group_detail_emotion_rank_no_presses)
        }

        GroupDetailEmotionRankingContent.NotListed -> {
            stringResource(Res.string.group_detail_rank_empty)
        }

        GroupDetailEmotionRankingContent.Unavailable -> {
            stringResource(Res.string.group_detail_emotion_rank_unavailable)
        }
    }

@Composable
private fun GroupDetailEmotionRankingUiState.supportingValues(
    weeklyPressCount: GroupDetailWeeklyPressCountUiState,
    pendingPressCount: Long,
): List<String> {
    val currentWeeklyTotal = weeklyPressCount.displayedTotal + pendingPressCount
    val weeklyCount =
        if (currentWeeklyTotal > 0L) {
            stringResource(
                Res.string.group_detail_emotion_rank_weekly_count,
                formatCount(currentWeeklyTotal),
            )
        } else {
            stringResource(Res.string.group_detail_rank_empty)
        }
    val statusMessage =
        when {
            isRefreshing || weeklyPressCount.isRefreshing -> {
                stringResource(Res.string.group_detail_rank_refreshing)
            }

            hasRefreshError || weeklyPressCount.hasRefreshError -> {
                stringResource(Res.string.group_detail_rank_refresh_error)
            }

            content.canRetry() -> {
                stringResource(Res.string.group_detail_emotion_rank_retry)
            }

            else -> {
                null
            }
        }

    return listOfNotNull(weeklyCount, statusMessage)
}

private fun GroupDetailEmotionRankingUiState.canRetry(weeklyPressCount: GroupDetailWeeklyPressCountUiState): Boolean =
    content.canRetry() || hasRefreshError || weeklyPressCount.hasRefreshError

private fun GroupDetailEmotionRankingContent.canRetry(): Boolean =
    this == GroupDetailEmotionRankingContent.NoPresses ||
        this == GroupDetailEmotionRankingContent.NotListed ||
        this == GroupDetailEmotionRankingContent.Unavailable

@Composable
internal fun GroupDetailSummaryValue(
    label: String,
    value: String,
    supportingValues: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.heightIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = Color(0xFF7B817B),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = AppColors.GroupInk,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        supportingValues.forEach {
            Text(
                text = it,
                color = Color(0xFF7B817B),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
