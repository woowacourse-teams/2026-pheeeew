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
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.record_group_accessibility
import pheeeew.shared.generated.resources.record_group_change
import pheeeew.shared.generated.resources.record_group_loading
import pheeeew.shared.generated.resources.record_group_none

@Composable
internal fun GroupSelectionButton(
    stamp: StampAppearanceUiModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    emptyLabel: String? = null,
    groupName: String? = null,
) {
    val resolvedEmptyLabel = emptyLabel ?: stringResource(Res.string.record_group_none)
    val resolvedGroupName = groupName ?: stamp?.label ?: resolvedEmptyLabel
    val accessibilityDescription = stringResource(Res.string.record_group_accessibility, resolvedGroupName)
    val actionLabel = stringResource(if (loading) Res.string.record_group_loading else Res.string.record_group_change)
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) { contentDescription = accessibilityDescription }
                .clickable(enabled = !loading, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GroupSelectionStamp(stamp = stamp, size = 36.dp, emptyLabel = resolvedEmptyLabel)
        Text(
            text = actionLabel,
            fontSize = 10.sp,
            color = AppColors.TextSecondary,
        )
    }
}
