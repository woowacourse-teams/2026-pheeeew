package com.pheeeew.feature.screens.map.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.map.record.group.GroupSelectionStamp
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_check

@Composable
internal fun FilterGroupsContent(
    state: MapFilterUiModel,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    Text("그룹 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier)
    when {
        state.loadingGroups -> {
            Text("그룹을 불러오는 중…", modifier = Modifier.padding(vertical = 32.dp))
        }

        state.groupLoadFailed -> {
            Text("그룹 목록을 불러오지 못했어요", modifier = Modifier.padding(vertical = 32.dp))
        }

        state.groups.isEmpty() -> {
            Text("선택할 그룹이 없어요", modifier = Modifier.padding(vertical = 32.dp))
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    contentPadding = PaddingValues(vertical = 20.dp),
                ) {
                    items(state.groups, key = { it.id }) { group ->
                        GroupFilterRow(group, group.id in state.selectedGroupIds) { onToggle(group.id) }
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(AppColors.Surface, AppColors.Surface.copy(alpha = 0f)),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(AppColors.Surface.copy(alpha = 0f), AppColors.Surface),
                            ),
                        ),
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    FilterActionButton("확인", onDone)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GroupFilterRow(
    group: GroupSelectorGroupUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupSelectionStamp(group.stamp, size = 36.dp, emptyLabel = group.name)
        Text(
            group.name,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier
                .size(24.dp)
                .border(1.5.dp, AppColors.GroupInk, CircleShape)
                .background(if (selected) AppColors.Primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Preview(name = "필터 그룹 다중 선택", widthDp = 402, heightDp = 560, showBackground = true)
@Composable
private fun FilterGroupsContentPreview() {
    val groups =
        listOf(
            GroupSelectorGroupUiModel(
                "1",
                "등산 모임",
                StampAppearanceUiModel("산", StampShapeId.CIRCLE, 0xFFFFE164, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "2",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "3",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "4",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "5",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "6",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "7",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "8",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "9",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "10",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "11",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "12",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "13",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "14",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "15",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "16",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "17",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
            GroupSelectorGroupUiModel(
                "18",
                "친구들",
                StampAppearanceUiModel("친구", StampShapeId.TICKET, 0xFFACD9EE, 0xFF202323),
            ),
        )
    AppTheme {
        Column(Modifier.background(AppColors.Surface).padding(horizontal = 24.dp)) {
            FilterGroupsContent(MapFilterUiModel(groups = groups, selectedGroupIds = setOf("mountain")), {}, {})
        }
    }
}
