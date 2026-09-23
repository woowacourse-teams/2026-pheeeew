package com.pheeeew.legacy.feature.map.sighlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pheeeew.legacy.core.designsystem.component.ConfirmDialog
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import com.pheeeew.legacy.core.navigation.PredictiveBackEffect
import com.pheeeew.legacy.feature.map.star.MapPinStar
import com.pheeeew.legacy.feature.map.star.toComposeStarColor
import kotlin.math.roundToInt

internal const val SIGH_BROWSER_EXIT_DURATION_MILLIS = 220L

@Composable
fun SighBrowserOverlay(
    visible: Boolean,
    listVisible: Boolean,
    items: List<SighListItemUiModel>,
    selectedItem: SighListItemUiModel?,
    selectedItemPositionPx: Offset?,
    isLoading: Boolean,
    isDetailLoading: Boolean = false,
    isLoadingMore: Boolean,
    isLoadMoreError: Boolean,
    refreshRevision: Long,
    canLoadMore: Boolean,
    errorMessage: String?,
    moderationUiState: SighModerationUiState,
    onItemClick: (SighListItemUiModel) -> Unit,
    onLikeClick: (Long, Boolean) -> Unit = { _, _ -> },
    onDismissList: () -> Unit,
    onDismissDetail: () -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onOpenActionMenu: (SighListItemUiModel) -> Unit,
    onDismissActionMenu: () -> Unit,
    onRequestBlock: () -> Unit,
    onDismissBlock: () -> Unit,
    onConfirmBlock: () -> Unit,
    onRequestReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showActionMenu = shouldShowSighActionMenu(selectedItem?.id, moderationUiState.actionTarget?.sighId)
    val detailStarRadiusPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    if (visible && !moderationUiState.isReportVisible) {
        PredictiveBackEffect(
            onProgress = {},
            onCompleted = {
                if (moderationUiState.blockTarget != null) {
                    onDismissBlock()
                } else if (showActionMenu) {
                    onDismissActionMenu()
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
                onMoreClick = { onOpenActionMenu(selectedItem) },
                onLikeClick = { liked -> onLikeClick(selectedItem.id, liked) },
                modifier = Modifier.fillMaxSize(),
            )

            selectedItemPositionPx?.let { position ->
                MapPinStar(
                    color = selectedItem.starStage.toComposeStarColor(),
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
                        .pointerInput(Unit) { detectTapGestures { onDismissActionMenu() } },
            )
        }

        AnimatedVisibility(
            visible =
                visible &&
                    (
                        showActionMenu ||
                            (listVisible && selectedItem == null && !isDetailLoading)
                    ),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(animationSpec = tween(260), initialOffsetY = { it }),
            exit =
                slideOutVertically(
                    animationSpec = tween(SIGH_BROWSER_EXIT_DURATION_MILLIS.toInt()),
                    targetOffsetY = { it },
                ),
        ) {
            BoxWithConstraints(contentAlignment = Alignment.BottomCenter) {
                if (listVisible && selectedItem == null && !isDetailLoading) {
                    SighListSheet(
                        items = items,
                        compact = false,
                        availableHeightPx = constraints.maxHeight.toFloat(),
                        isLoading = isLoading,
                        isLoadingMore = isLoadingMore,
                        isLoadMoreError = isLoadMoreError,
                        refreshRevision = refreshRevision,
                        canLoadMore = canLoadMore,
                        errorMessage = errorMessage,
                        onItemClick = onItemClick,
                        onLikeClick = onLikeClick,
                        onDismissList = onDismissList,
                        onCompactBackgroundClick = onDismissDetail,
                        onLoadMore = onLoadMore,
                        onRefresh = onRefresh,
                    )
                }

                AnimatedVisibility(
                    visible = showActionMenu && selectedItem != null,
                    enter = slideInVertically(animationSpec = tween(260), initialOffsetY = { it }),
                    exit = slideOutVertically(animationSpec = tween(220), targetOffsetY = { it }),
                ) {
                    if (selectedItem != null) {
                        SighActionSheet(
                            onReportClick = {
                                onRequestReport()
                            },
                            onBlockClick = {
                                onRequestBlock()
                            },
                            onCancelClick = onDismissActionMenu,
                        )
                    }
                }
            }
        }

        moderationUiState.blockTarget?.let {
            ConfirmDialog(
                title = "해당 사용자를 차단하시겠습니까?",
                body = "차단 이후 해당 사용자가 올린 한숨은 더 이상 보이지 않으며 다시 해제할 수 없습니다.",
                confirmText = "차단하기",
                onConfirmClick = onConfirmBlock,
                onDismissRequest = onDismissBlock,
                onDismissClick = onDismissBlock,
            )
        }
    }
}

internal fun shouldShowSighActionMenu(
    selectedSighId: Long?,
    actionTargetSighId: Long?,
): Boolean = selectedSighId != null && selectedSighId == actionTargetSighId

@Preview
@Composable
private fun SighBrowserListPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.Navy900)) {
            SighBrowserOverlay(
                visible = true,
                listVisible = true,
                items = emptyList(),
                selectedItem = null,
                selectedItemPositionPx = null,
                isLoading = false,
                isLoadingMore = false,
                isLoadMoreError = false,
                refreshRevision = 0L,
                canLoadMore = false,
                errorMessage = null,
                moderationUiState = SighModerationUiState(),
                onItemClick = {},
                onDismissList = {},
                onDismissDetail = {},
                onLoadMore = {},
                onRefresh = {},
                onOpenActionMenu = {},
                onDismissActionMenu = {},
                onRequestBlock = {},
                onDismissBlock = {},
                onConfirmBlock = {},
                onRequestReport = {},
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
                listVisible = true,
                items = emptyList(),
                selectedItem = null,
                selectedItemPositionPx = null,
                isLoading = false,
                isLoadingMore = false,
                isLoadMoreError = false,
                refreshRevision = 0L,
                canLoadMore = false,
                errorMessage = null,
                moderationUiState = SighModerationUiState(),
                onItemClick = {},
                onDismissList = {},
                onDismissDetail = {},
                onLoadMore = {},
                onRefresh = {},
                onOpenActionMenu = {},
                onDismissActionMenu = {},
                onRequestBlock = {},
                onDismissBlock = {},
                onConfirmBlock = {},
                onRequestReport = {},
            )
        }
    }
}
