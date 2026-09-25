package com.pheeeew.feature.screens.group.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.GroupStamp
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_home_member_and_weekly_count

/** 목록 전체를 하나의 그룹 진입 버튼으로 표현합니다. */
@Composable
fun GroupListItem(
    group: GroupSummaryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(86.dp)
                .drawBehind {
                    val shadowOffset = 3.dp.toPx()
                    drawRoundRect(
                        color = AppColors.GroupInk,
                        topLeft = Offset(0f, shadowOffset),
                        size = Size(size.width, size.height - shadowOffset),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }.clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(83.dp)
                    .clip(shape)
                    .background(Color.White)
                    .border(width = 1.dp, color = AppColors.GroupInk, shape = shape)
                    .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupStamp(appearance = group.stamp, size = 52.dp)
            Column(
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = group.name,
                    color = AppColors.RankingContent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            Res.string.group_home_member_and_weekly_count,
                            group.memberCount,
                            group.weeklyStampCount,
                        ),
                    color = AppColors.RankingSecondaryContent,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
