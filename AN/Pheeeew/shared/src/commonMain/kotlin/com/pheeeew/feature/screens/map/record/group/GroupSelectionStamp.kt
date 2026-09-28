package com.pheeeew.feature.screens.map.record.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId

@Composable
fun GroupSelectionStamp(
    stamp: StampAppearanceUiModel?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (stamp != null) {
        GroupStamp(appearance = stamp, size = size, modifier = modifier)
    } else {
        Box(
            modifier = modifier.size(size).background(AppColors.Gray100, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("없음", color = AppColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(name = "그룹 선택 스탬프", showBackground = true)
@Composable
private fun GroupSelectionStampPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupSelectionStamp(stamp = null, size = 70.dp)
        GroupSelectionStamp(
            stamp = StampAppearanceUiModel("모임", StampShapeId.TICKET, 0xFFFFE164, 0xFF252826),
            size = 70.dp,
        )
    }
}
