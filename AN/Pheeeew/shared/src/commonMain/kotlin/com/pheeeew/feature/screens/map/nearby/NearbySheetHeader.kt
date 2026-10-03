package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.noRippleClickable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_refresh
import pheeeew.shared.generated.resources.map_refresh_nearby

@Composable
internal fun NearbySheetHeader(
    group: GroupSelectorGroupUiModel,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenGroups: () -> Unit,
) {
    val refreshDescription = stringResource(Res.string.map_refresh_nearby)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .semantics { contentDescription = refreshDescription }
                .noRippleClickable(enabled = !loading, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_refresh),
                contentDescription = null,
                tint = Color(0xFF252826),
                modifier = Modifier.size(20.dp),
            )
        }
        NearbyGroupFilter(group, onClick = onOpenGroups)
    }
}
