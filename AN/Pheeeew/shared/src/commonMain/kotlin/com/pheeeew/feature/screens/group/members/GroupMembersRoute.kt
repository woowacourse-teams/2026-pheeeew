package com.pheeeew.feature.screens.group.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun GroupMembersRoute(
    viewModel: GroupMembersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GroupMembersScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadNextPage,
        modifier = modifier,
    )
}
