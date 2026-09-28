package com.pheeeew.feature.screens.group.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.detail.component.EmotionPad
import com.pheeeew.feature.screens.group.detail.component.GroupSummary
import com.pheeeew.feature.screens.group.detail.component.formatCount
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailCopyKey
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupRankUiModel
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_first_summary
import pheeeew.shared.generated.resources.group_detail_hero_active
import pheeeew.shared.generated.resources.group_detail_hero_active_subtitle
import pheeeew.shared.generated.resources.group_detail_hero_first
import pheeeew.shared.generated.resources.group_detail_hero_first_subtitle
import pheeeew.shared.generated.resources.group_detail_hero_neutral
import pheeeew.shared.generated.resources.group_detail_hero_neutral_subtitle
import pheeeew.shared.generated.resources.group_detail_press_check
import pheeeew.shared.generated.resources.group_detail_press_checking
import pheeeew.shared.generated.resources.group_detail_press_unknown
import pheeeew.shared.generated.resources.group_detail_rank_empty
import pheeeew.shared.generated.resources.group_detail_rank_label
import pheeeew.shared.generated.resources.group_detail_rank_number
import pheeeew.shared.generated.resources.group_detail_refresh_error_pull
import pheeeew.shared.generated.resources.group_detail_summary_angry
import pheeeew.shared.generated.resources.group_detail_summary_annoyed
import pheeeew.shared.generated.resources.group_detail_summary_blocked
import pheeeew.shared.generated.resources.group_detail_summary_defeated
import pheeeew.shared.generated.resources.group_detail_summary_neutral
import pheeeew.shared.generated.resources.group_detail_summary_tired
import pheeeew.shared.generated.resources.group_detail_today_total
import pheeeew.shared.generated.resources.group_detail_total_count
import pheeeew.shared.generated.resources.group_detail_weekly_label
import pheeeew.shared.generated.resources.group_detail_weekly_value

/** 402dp 시안의 세로 순서를 유지하고, 작은 화면에서는 전체 내용을 스크롤합니다. */
@Composable
internal fun GroupDetailReadyContent(
    detail: GroupDetailUiModel,
    hasRefreshError: Boolean,
    canTapEmotion: Boolean,
    pressStatus: GroupPressStatus,
    pendingEmotionPresses: Map<EmotionKind, Long> = emptyMap(),
    onInviteClick: () -> Unit,
    onRetry: () -> Unit,
    onEmotionTap: (EmotionKind) -> Boolean,
    onResolvePressOutcome: () -> Unit,
    fixtureFeedbackOnAcceptedPress: Boolean = false,
    feedbackOperationKey: () -> com.pheeeew.feature.screens.group.model.GroupOperationKey? = { null },
    onFeedbackShown: (com.pheeeew.feature.screens.group.model.GroupOperationKey) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            if (hasRefreshError) {
                RefreshBanner(onRetry = onRetry)
            }
            GroupSummary(memberCount = detail.group.memberCount, onInviteClick = onInviteClick)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(detail.presentation.heroTitle.toStringResource()),
                color = AppColors.GroupInk,
                fontSize = if (detail.presentation.kind == GroupDetailPresentationKind.FirstStart) 24.sp else 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(detail.presentation.heroSubtitle.toStringResource()),
                color = Color(0xFF7B817B),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(36.dp))
            PressStatusNotice(status = pressStatus, onResolveOutcome = onResolvePressOutcome)
            EmotionPad(
                counts = detail.emotionCounts,
                optimisticPressCounts = pendingEmotionPresses,
                enabled = canTapEmotion,
                onEmotionTap = onEmotionTap,
                fixtureFeedbackOnAcceptedPress = fixtureFeedbackOnAcceptedPress,
                feedbackOperationKey = feedbackOperationKey,
                onFeedbackShown = onFeedbackShown,
                preserveFeedbackWhileDisabled = pressStatus != GroupPressStatus.Idle,
            )
            Spacer(Modifier.height(20.dp))
            TodayTotal(detail.copy(todayTotal = detail.todayTotal + pendingEmotionPresses.values.sum()))
            Spacer(Modifier.height(24.dp))
            WeeklySummary(detail)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 본문")
@Composable
private fun GroupDetailReadyContentPreview() {
    GroupDetailReadyContent(
        detail = fixtureDetail(1_238L, GroupDetailPresentationKind.Active),
        hasRefreshError = false,
        canTapEmotion = true,
        pressStatus = GroupPressStatus.Idle,
        onInviteClick = {},
        onRetry = {},
        onEmotionTap = { true },
        onResolvePressOutcome = {},
    )
}

@Composable
private fun PressStatusNotice(
    status: GroupPressStatus,
    onResolveOutcome: () -> Unit,
) {
    when (status) {
        GroupPressStatus.Idle,
        is GroupPressStatus.Sending,
        -> {
            Unit
        }

        is GroupPressStatus.Reconciling -> {
            val message = stringResource(Res.string.group_detail_press_checking)
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = Color(0xFF7B817B),
                fontSize = 13.sp,
            )
        }

        is GroupPressStatus.OutcomeUnknown -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.group_detail_press_unknown),
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF7B817B),
                    fontSize = 13.sp,
                )
                TextButton(onClick = onResolveOutcome) {
                    Text(text = stringResource(Res.string.group_detail_press_check), color = AppColors.GroupInk)
                }
            }
        }
    }
}

@Composable
private fun TodayTotal(detail: GroupDetailUiModel) {
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.group_detail_today_total),
            modifier = Modifier.weight(1f),
            color = Color(0xFF7B817B),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.group_detail_total_count, formatCount(detail.todayTotal)),
            color = AppColors.GroupInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(8.dp))
    val summary =
        if (detail.presentation.kind == GroupDetailPresentationKind.FirstStart) {
            stringResource(Res.string.group_detail_first_summary)
        } else {
            detail.presentation.summaryMessage
                ?.let { stringResource(it.toStringResource()) }
                .orEmpty()
        }
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F2EC)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = summary, color = AppColors.GroupInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WeeklySummary(detail: GroupDetailUiModel) {
    val weeklyScore = requireNotNull(detail.group.weeklyStampCount)
    HorizontalDivider(color = Color(0xFFDFE2D9), thickness = 1.dp)
    Row(Modifier.fillMaxWidth().height(69.dp), verticalAlignment = Alignment.CenterVertically) {
        SummaryValue(
            label = stringResource(Res.string.group_detail_weekly_label),
            value = stringResource(Res.string.group_detail_weekly_value, formatCount(weeklyScore)),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(1.dp).height(69.dp).background(Color(0xFFDFE2D9)))
        SummaryValue(
            label = stringResource(Res.string.group_detail_rank_label),
            value =
                when (val rank = detail.rank) {
                    is GroupRankUiModel.Ranked -> stringResource(Res.string.group_detail_rank_number, rank.value)
                    else -> stringResource(Res.string.group_detail_rank_empty)
                },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0xFF7B817B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            color = AppColors.GroupInk,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun DetailOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = CircleShape,
        border = BorderStroke(AppBorders.Standard, AppColors.GroupInk),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = AppColors.GroupInk,
                disabledContentColor = AppColors.GroupInk,
            ),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 8.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun GroupDetailCopyKey.toStringResource() =
    when (this) {
        GroupDetailCopyKey.FirstStartHeroTitle -> Res.string.group_detail_hero_first
        GroupDetailCopyKey.FirstStartHeroSubtitle -> Res.string.group_detail_hero_first_subtitle
        GroupDetailCopyKey.ActiveHeroTitle -> Res.string.group_detail_hero_active
        GroupDetailCopyKey.ActiveHeroSubtitle -> Res.string.group_detail_hero_active_subtitle
        GroupDetailCopyKey.NeutralHeroTitle -> Res.string.group_detail_hero_neutral
        GroupDetailCopyKey.NeutralHeroSubtitle -> Res.string.group_detail_hero_neutral_subtitle
        GroupDetailCopyKey.SummaryBlocked -> Res.string.group_detail_summary_blocked
        GroupDetailCopyKey.SummaryAnnoyed -> Res.string.group_detail_summary_annoyed
        GroupDetailCopyKey.SummaryTired -> Res.string.group_detail_summary_tired
        GroupDetailCopyKey.SummaryDefeated -> Res.string.group_detail_summary_defeated
        GroupDetailCopyKey.SummaryAngry -> Res.string.group_detail_summary_angry
        GroupDetailCopyKey.SummaryNeutral -> Res.string.group_detail_summary_neutral
    }

@Composable
private fun RefreshBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(Res.string.group_detail_refresh_error_pull),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF3F4F2))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        color = AppColors.RankingSecondaryContent,
        fontSize = 12.sp,
    )
}
