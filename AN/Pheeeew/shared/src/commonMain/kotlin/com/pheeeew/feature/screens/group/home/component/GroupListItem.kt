package com.pheeeew.feature.screens.group.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** A group the current user has already joined. */
@Composable
fun GroupListItem(
    group: GroupSummaryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightQuery: String = "",
) {
    GroupCatalogCard(
        group = group,
        joinType = null,
        isJoined = true,
        onClick = onClick,
        modifier = modifier,
        highlightQuery = highlightQuery,
    )
}
