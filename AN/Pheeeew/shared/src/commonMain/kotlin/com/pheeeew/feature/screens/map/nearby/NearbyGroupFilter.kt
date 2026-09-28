package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.noRippleClickable

@Composable
internal fun NearbyGroupFilter(
    group: GroupSelectorGroupUiModel,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "${group.name}, 그룹 변경" }
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (group.showStamp) {
            SelectorStamp(group, size = 32)
        } else {
            Text("전체", color = Color(0xFF252826), fontSize = 14.sp)
        }
        Text("그룹 변경", color = Color(0xFF85877F), fontSize = 10.sp)
    }
}
