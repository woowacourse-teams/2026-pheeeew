package com.pheeeew.feature.screens.group.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import pheeeew.shared.generated.resources.group_home_joined
import pheeeew.shared.generated.resources.group_home_member_count
import pheeeew.shared.generated.resources.group_home_private
import pheeeew.shared.generated.resources.group_home_public

@Composable
fun GroupCatalogCard(
    group: GroupSummaryUiModel,
    joinType: GroupJoinType?,
    isJoined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(96.dp)
                .drawBehind {
                    val shadowOffset = 3.dp.toPx()
                    drawRoundRect(
                        color = AppColors.GroupInk,
                        topLeft = Offset(0f, shadowOffset),
                        size = Size(size.width, size.height - shadowOffset),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(93.dp)
                    .raisedPressEffect(interactionSource)
                    .clip(shape)
                    .background(Color.White)
                    .border(width = AppBorders.Standard, color = AppColors.GroupInk, shape = shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    ).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupStamp(appearance = group.stamp, size = 44.dp)
            Column(
                modifier = Modifier.weight(1f).padding(start = 13.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = group.name,
                    color = AppColors.RankingContent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.group_home_member_count, group.memberCount),
                        color = AppColors.RankingSecondaryContent,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                    if (joinType != null) {
                        CatalogBadge(
                            text =
                                stringResource(
                                    if (joinType == GroupJoinType.PUBLIC) {
                                        Res.string.group_home_public
                                    } else {
                                        Res.string.group_home_private
                                    },
                                ),
                            background = Color(0xFFF0F2EF),
                            textColor = AppColors.RankingSecondaryContent,
                        )
                    }
                    if (isJoined) {
                        CatalogBadge(
                            text = stringResource(Res.string.group_home_joined),
                            background = Color(0xFFDDF4EC),
                            textColor = Color(0xFF417567),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogBadge(
    text: String,
    background: Color,
    textColor: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(6.dp),
                ).background(background)
                .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor, fontSize = 10.sp, maxLines = 1)
    }
}
