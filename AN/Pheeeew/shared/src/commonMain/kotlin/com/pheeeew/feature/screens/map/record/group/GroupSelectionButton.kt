package com.pheeeew.feature.screens.map.record.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel

@Composable
internal fun GroupSelectionButton(
    stamp: StampAppearanceUiModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    emptyLabel: String = "없음",
    groupName: String = stamp?.label ?: emptyLabel,
) {
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) { contentDescription = "$groupName, 그룹 변경" }
                .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GroupSelectionStamp(stamp = stamp, size = 44.dp, emptyLabel = emptyLabel)
        Text(
            text = if (loading) "그룹 확인 중" else "그룹 변경",
            fontSize = 10.sp,
            color = AppColors.TextSecondary,
        )
    }
}
