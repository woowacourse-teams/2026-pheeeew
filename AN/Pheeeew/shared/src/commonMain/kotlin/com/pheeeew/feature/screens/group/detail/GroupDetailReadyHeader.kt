package com.pheeeew.feature.screens.group.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.group.detail.model.GroupDetailReadyUiModel
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_invite_code
import pheeeew.shared.generated.resources.group_detail_member_count
import pheeeew.shared.generated.resources.group_detail_rank_empty
import pheeeew.shared.generated.resources.group_detail_rank_number

@Composable
internal fun GroupProfile(
    group: GroupDetailReadyUiModel,
    onInviteClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.group_detail_member_count, group.memberCount),
                color = AppColors.GroupInk,
                fontSize = 12.sp,
            )
            if (group.role != GroupRole.NONE) {
                Box(
                    Modifier
                        .background(Color(0xFFF0F1ED), CircleShape)
                        .clickable(role = Role.Button, onClick = onInviteClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(Res.string.group_detail_invite_code),
                        color = AppColors.GroupInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GroupStamp(appearance = group.stamp, size = 84.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = group.description.orEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp),
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
        )
    }
}

@Composable
internal fun GroupStatistics(group: GroupDetailReadyUiModel) {
    HorizontalDivider(color = AppColors.BorderLight)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Statistic(
            label = "이번주 스탬프",
            rank = group.weeklyStampRank,
            count = "(${formatCount(group.weeklyStampCount)}개)",
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).height(54.dp).background(AppColors.BorderLight))
        Statistic(
            label = "이번주 프레스",
            rank = group.weeklyEmotionPressRank,
            count = "(${formatCount(group.weeklyEmotionPressCount)}번)",
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = Rule)
}

@Composable
private fun Statistic(
    label: String,
    rank: Int?,
    count: String,
    modifier: Modifier,
) {
    Column(
        modifier.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            modifier = Modifier.align(Alignment.Start).padding(start = 4.dp),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                rank?.let { stringResource(Res.string.group_detail_rank_number, it) }
                    ?: stringResource(Res.string.group_detail_rank_empty),
            modifier = Modifier,
            color = AppColors.GroupInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count,
            modifier = Modifier.padding(end = 4.dp).align(Alignment.End),
            color = AppColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

private val Rule = Color(0xFFDFE2D9)

private fun formatCount(count: Long): String = count.toString()

@Preview(widthDp = 402, name = "그룹 상세 · 헤더", showBackground = true)
@Composable
private fun GroupDetailHeaderPreview() {
    val group = previewGroupDetailReadyUiModel()
    Column(Modifier.padding(horizontal = 24.dp)) {
        GroupProfile(group, onInviteClick = {})
        Spacer(Modifier.height(18.dp))
        GroupStatistics(group)
    }
}

@Preview(widthDp = 402, name = "그룹 상세 · 미가입 헤더", showBackground = true)
@Composable
private fun GroupDetailGuestHeaderPreview() {
    val group = previewGroupDetailReadyUiModel(GroupRole.NONE)
    Column(Modifier.padding(horizontal = 24.dp)) {
        GroupProfile(group, onInviteClick = {})
        Spacer(Modifier.height(18.dp))
        GroupStatistics(group)
    }
}

@Preview(widthDp = 402, name = "그룹 상세 · 순위 미집계", showBackground = true)
@Composable
private fun GroupDetailUnrankedHeaderPreview() {
    val group =
        previewGroupDetailReadyUiModel().copy(
            weeklyStampCount = 0,
            weeklyStampRank = null,
            weeklyEmotionPressCount = 0,
            weeklyEmotionPressRank = null,
        )
    Column(Modifier.padding(horizontal = 24.dp)) {
        GroupProfile(group, onInviteClick = {})
        Spacer(Modifier.height(18.dp))
        GroupStatistics(group)
    }
}
