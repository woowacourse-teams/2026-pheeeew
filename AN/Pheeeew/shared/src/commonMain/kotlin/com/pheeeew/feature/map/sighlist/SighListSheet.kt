package com.pheeeew.feature.map.sighlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_refresh

@Composable
internal fun SighListSheet(
    items: List<SighListItemUiModel>,
    compact: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    isLoadMoreError: Boolean,
    refreshRevision: Long,
    canLoadMore: Boolean,
    errorMessage: String?,
    onItemClick: (SighListItemUiModel) -> Unit,
    onDismissList: () -> Unit,
    onCompactBackgroundClick: () -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
    var downwardDrag by remember(compact) { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val sheetHeight = if (compact) Modifier.heightIn(min = 174.dp, max = 210.dp) else Modifier.fillMaxHeight(0.52f)
    val listState = rememberLazyListState()

    LaunchedEffect(listState, items.size, compact, canLoadMore, isLoadingMore) {
        if (compact || !canLoadMore || isLoadingMore) return@LaunchedEffect
        snapshotFlow {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            items.isNotEmpty() && lastVisibleIndex >= items.lastIndex - 2
        }.distinctUntilChanged().collect { shouldLoadMore ->
            if (shouldLoadMore) onLoadMore()
        }
    }

    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0L && items.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(sheetHeight)
                .graphicsLayer { translationY = downwardDrag }
                .pointerInput(compact, onCompactBackgroundClick) {
                    detectTapGestures {
                        if (compact) onCompactBackgroundClick()
                    }
                },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = AppColors.Navy800,
        contentColor = AppColors.Cream100,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            SighListHeader(
                compact = compact,
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                onDismissList = onDismissList,
                onCompactBackgroundClick = onCompactBackgroundClick,
                onDragStarted = { downwardDrag = 0f },
                onDownwardDrag = { downwardDrag += it },
                onDragFinished = {
                    if (downwardDrag >= dismissThresholdPx) onDismissList()
                    downwardDrag = 0f
                },
                onDragCancelled = { downwardDrag = 0f },
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            top = 2.dp,
                            end = 16.dp,
                            bottom = if (compact) 12.dp else 20.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = !compact,
                ) {
                    if (isLoading && items.isEmpty()) {
                        item(key = "sigh-list-loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = AppColors.Blue100,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    } else if (errorMessage != null && items.isEmpty()) {
                        item(key = "sigh-list-error") {
                            SighListError(message = errorMessage, onRetry = onRefresh)
                        }
                    } else if (items.isEmpty()) {
                        item(key = "empty-sigh-list") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "아직 근처에 머무는 한숨이 없어요",
                                    style = AppTheme.typography.menuItem,
                                    color = AppTheme.colors.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(items, key = { it.id }) { item ->
                            SighListItem(
                                item = item,
                                onClick = { onItemClick(item) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (isLoadingMore) {
                            item(key = "sigh-list-loading-more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = AppColors.Blue100,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                    )
                                }
                            }
                        } else if (errorMessage != null) {
                            item(key = "sigh-list-load-more-error") {
                                SighListError(
                                    message = errorMessage,
                                    onRetry = if (isLoadMoreError) onLoadMore else onRefresh,
                                )
                            }
                        }
                    }
                }

                if (isLoading && items.isNotEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(AppColors.Navy800.copy(alpha = 0.72f))
                                .pointerInput(Unit) { detectTapGestures { } },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = AppColors.Blue100,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SighListError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = message,
            style = AppTheme.typography.caption,
            color = AppTheme.colors.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(text = "다시 시도")
        }
    }
}

@Composable
private fun SighListHeader(
    compact: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDismissList: () -> Unit,
    onCompactBackgroundClick: () -> Unit,
    onDragStarted: () -> Unit,
    onDownwardDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    onDragCancelled: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .pointerInput(onDismissList, compact) {
                    detectVerticalDragGestures(
                        onDragStart = { onDragStarted() },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f) {
                                change.consume()
                                onDownwardDrag(dragAmount)
                            }
                        },
                        onDragEnd = { onDragFinished() },
                        onDragCancel = onDragCancelled,
                    )
                }.then(if (compact) Modifier.clickable(onClick = onCompactBackgroundClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(AppColors.Cream100.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    .semantics { contentDescription = "아래로 밀어 한숨 목록 닫기" },
        )
        Box(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Text(
                text = "근처 한숨",
                modifier = Modifier.align(Alignment.CenterStart),
                style = AppTheme.typography.sectionHeader,
            )
            if (!compact) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = "현재 지도 영역의 한숨 새로고침",
                        tint = AppColors.Cream100,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SighListItem(
    item: SighListItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Navy700,
        contentColor = AppColors.Cream100,
        border = BorderStroke(1.dp, AppTheme.colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SighStar(color = item.starColor, modifier = Modifier.size(26.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.nickname,
                        style = AppTheme.typography.caption.copy(fontWeight = FontWeight.Normal),
                    )
                    Text(
                        text = "  ·  ${item.relativeTime}",
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.memo,
                    style = AppTheme.typography.menuItem,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SighStar(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush =
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                    center,
                    size.minDimension / 2f,
                ),
            radius = size.minDimension / 2f,
            center = center,
        )
        drawCircle(color = AppColors.Cream100, radius = size.minDimension * 0.14f, center = center)
    }
}
