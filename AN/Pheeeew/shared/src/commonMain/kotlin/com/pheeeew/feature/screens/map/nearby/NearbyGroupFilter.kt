package com.pheeeew.feature.screens.map.nearby

import androidx.compose.runtime.Composable
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
