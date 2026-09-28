package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.map.record.group.GroupSelectionButton
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel

@Composable
internal fun NearbyGroupFilter(
    group: GroupSelectorGroupUiModel,
    onClick: () -> Unit,
) {
    GroupSelectionButton(
        stamp = group.stamp.takeIf { group.showStamp },
        emptyLabel = group.name,
        groupName = group.name,
        onClick = onClick,
    )
}

internal val nearbyPreviewGroup =
    GroupSelectorGroupUiModel(
        "walk",
        "산책 모임",
        StampAppearanceUiModel("산책", StampShapeId.FLOWER, 0xFFACD9EE, 0xFF252826),
    )

@Preview(name = "Nearby · 그룹 필터 전체 / 선택", showBackground = true)
@Composable
private fun NearbyGroupFiltersPreview() {
    AppTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            NearbyGroupFilter(ALL_GROUP_OPTION, onClick = {})
            NearbyGroupFilter(nearbyPreviewGroup, onClick = {})
        }
    }
}
