package com.pheeeew.feature.screens.group.home.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.raisedPressEffect
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_member_count
import pheeeew.shared.generated.resources.group_home_joined
import pheeeew.shared.generated.resources.group_home_private
import pheeeew.shared.generated.resources.group_home_public

@Composable
fun GroupCatalogCard(
    group: GroupSummaryUiModel,
    joinType: GroupJoinType?,
    isJoined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlightQuery: String = "",
    showJoinedStatus: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    val memberCount = stringResource(Res.string.group_detail_member_count, group.memberCount)
    val visibility =
        joinType?.let {
            stringResource(
                if (it ==
                    GroupJoinType.PUBLIC
                ) {
                    Res.string.group_home_public
                } else {
                    Res.string.group_home_private
                },
            )
        }
    val joined = stringResource(Res.string.group_home_joined)
    Box(
        modifier.fillMaxWidth().drawBehind {
            val shadowOffset = 3.dp.toPx()
            drawRoundRect(
                color = AppColors.GroupInk,
                topLeft = Offset(0f, shadowOffset),
                size = Size(size.width, size.height - shadowOffset),
                cornerRadius = CornerRadius(16.dp.toPx()),
            )
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp)
                    .heightIn(min = 80.dp)
                    .raisedPressEffect(interactionSource, enabled = enabled, pressedOffset = 1.dp)
                    .clip(shape)
                    .background(AppColors.Surface)
                    .border(AppBorders.Standard, AppColors.GroupInk, shape)
                    .semantics {
                        if (isJoined && showJoinedStatus) stateDescription = joined
                    }.clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    ).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp)) {
                GroupStamp(appearance = group.stamp, size = 44.dp)
                if (isJoined && showJoinedStatus) {
                    JoinedStampCheck(Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
                }
            }
            Column(
                Modifier.weight(1f).padding(start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = highlightGroupName(group.name, highlightQuery),
                    color = AppColors.GroupInk,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = if (highlightQuery.isBlank()) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = memberCount,
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                    )
                    if (visibility != null) {
                        Text("·", color = AppColors.TextSecondary, fontSize = 12.sp)
                        Box(
                            Modifier.size(6.dp).background(
                                if (joinType ==
                                    GroupJoinType.PUBLIC
                                ) {
                                    AppColors.RankingAccent
                                } else {
                                    AppColors.TextSecondary
                                },
                                CircleShape,
                            ),
                        )
                        Text(
                            text = visibility,
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = "›",
                modifier = Modifier.clearAndSetSemantics {},
                color = AppColors.TextSecondary,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun JoinedStampCheck(modifier: Modifier = Modifier) {
    val ink = AppColors.GroupInk
    Box(
        modifier
            .size(20.dp)
            .clearAndSetSemantics {}
            .background(AppColors.RankingAccent, CircleShape)
            .border(AppBorders.Standard, ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) {
            val middle = Offset(size.width * .4f, size.height * .75f)
            drawLine(ink, Offset(size.width * .1f, size.height * .45f), middle, 1.8.dp.toPx(), StrokeCap.Round)
            drawLine(ink, middle, Offset(size.width * .9f, size.height * .2f), 1.8.dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun highlightGroupName(
    name: String,
    query: String,
): AnnotatedString =
    buildAnnotatedString {
        append(name)
        val keyword = query.trim()
        if (keyword.isNotEmpty()) {
            var start = name.indexOf(keyword, ignoreCase = true)
            while (start >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + keyword.length)
                start = name.indexOf(keyword, start + keyword.length, ignoreCase = true)
            }
        }
    }
