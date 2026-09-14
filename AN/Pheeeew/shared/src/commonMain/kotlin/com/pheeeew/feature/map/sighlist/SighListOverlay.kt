package com.pheeeew.feature.map.sighlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.navigation.PredictiveBackEffect
import kotlin.math.roundToInt

@Composable
fun SighBrowserOverlay(
    visible: Boolean,
    items: List<SighListItemUiModel>,
    selectedItem: SighListItemUiModel?,
    selectedItemPositionPx: Offset?,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    isLoadMoreError: Boolean,
    refreshRevision: Long,
    canLoadMore: Boolean,
    errorMessage: String?,
    onItemClick: (SighListItemUiModel) -> Unit,
    onDismissList: () -> Unit,
    onDismissDetail: () -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onBlockClick: (SighListItemUiModel) -> Unit = {},
    onReportClick: (SighListItemUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showActionMenu by remember(selectedItem?.id) { mutableStateOf(false) }
    val detailStarRadiusPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    if (visible) {
        PredictiveBackEffect(
            onProgress = {},
            onCompleted = {
                if (showActionMenu) {
                    showActionMenu = false
                } else if (selectedItem != null) {
                    onDismissDetail()
                } else {
                    onDismissList()
                }
            },
            onCancelled = {},
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (visible && selectedItem != null) {
            SighDetailModal(
                item = selectedItem,
                onDismiss = onDismissDetail,
                onMoreClick = { showActionMenu = true },
                modifier = Modifier.fillMaxSize(),
            )

            selectedItemPositionPx?.let { position ->
                SighStar(
                    color = selectedItem.starColor,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    x = (position.x - detailStarRadiusPx).roundToInt(),
                                    y = (position.y - detailStarRadiusPx).roundToInt(),
                                )
                            }.size(48.dp)
                            .zIndex(1f),
                )
            }
        }

        if (showActionMenu) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { showActionMenu = false } },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(animationSpec = tween(260), initialOffsetY = { it }),
            exit = slideOutVertically(animationSpec = tween(220), targetOffsetY = { it }),
        ) {
            if (showActionMenu && selectedItem != null) {
                SighActionSheet(
                    onReportClick = {
                        showActionMenu = false
                        onReportClick(selectedItem)
                    },
                    onBlockClick = {
                        showActionMenu = false
                        onBlockClick(selectedItem)
                    },
                    onCancelClick = { showActionMenu = false },
                )
            } else {
                SighListSheet(
                    items = if (selectedItem == null) items else listOf(selectedItem),
                    compact = selectedItem != null,
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    isLoadMoreError = isLoadMoreError,
                    refreshRevision = refreshRevision,
                    canLoadMore = canLoadMore,
                    errorMessage = errorMessage,
                    onItemClick =
                        if (selectedItem == null) {
                            onItemClick
                        } else {
                            { _: SighListItemUiModel -> onDismissDetail() }
                        },
                    onDismissList = onDismissList,
                    onCompactBackgroundClick = onDismissDetail,
                    onLoadMore = onLoadMore,
                    onRefresh = onRefresh,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SighBrowserListPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.Navy900)) {
            SighBrowserOverlay(
                visible = true,
                items = emptyList(),
                selectedItem = null,
                selectedItemPositionPx = null,
                isLoading = false,
                isLoadingMore = false,
                isLoadMoreError = false,
                refreshRevision = 0L,
                canLoadMore = false,
                errorMessage = null,
                onItemClick = {},
                onDismissList = {},
                onDismissDetail = {},
                onLoadMore = {},
                onRefresh = {},
            )
        }
    }
}

@Preview
@Composable
private fun SighBrowserDetailPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.Navy900)) {
            SighBrowserOverlay(
                visible = true,
                items = emptyList(),
                selectedItem = null,
                selectedItemPositionPx = null,
                isLoading = false,
                isLoadingMore = false,
                isLoadMoreError = false,
                refreshRevision = 0L,
                canLoadMore = false,
                errorMessage = null,
                onItemClick = {},
                onDismissList = {},
                onDismissDetail = {},
                onLoadMore = {},
                onRefresh = {},
            )
        }
    }
}
