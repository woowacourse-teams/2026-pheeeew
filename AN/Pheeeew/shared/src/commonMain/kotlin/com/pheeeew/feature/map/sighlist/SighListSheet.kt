package com.pheeeew.feature.map.sighlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.map.star.MapPinStar
import com.pheeeew.feature.map.star.StarAgeStage
import com.pheeeew.feature.map.star.toComposeStarColor
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_favorite
import pheeeew.shared.generated.resources.ic_favorite_border
import pheeeew.shared.generated.resources.ic_refresh

private enum class SighListSheetLevel {
    Expanded,
    Middle,
}

@Composable
internal fun SighListSheet(
    items: List<SighListItemUiModel>,
    compact: Boolean,
    availableHeightPx: Float,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    isLoadMoreError: Boolean,
    refreshRevision: Long,
    canLoadMore: Boolean,
    errorMessage: String?,
    onItemClick: (SighListItemUiModel) -> Unit,
    onLikeClick: (Long, Boolean) -> Unit = { _, _ -> },
    onDismissList: () -> Unit,
    onCompactBackgroundClick: () -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    var compactDownwardDrag by remember(compact) { mutableFloatStateOf(0f) }
    var sheetLevel by remember(compact) { mutableStateOf(SighListSheetLevel.Middle) }
    var isDraggingSheet by remember(compact) { mutableStateOf(false) }
    var isListDraggingSheet by remember(compact) { mutableStateOf(false) }
    var draggedSheetOffsetPx by remember(compact) { mutableFloatStateOf(0f) }
    var totalDragPx by remember(compact) { mutableFloatStateOf(0f) }

    val middleOffsetPx = availableHeightPx * 0.55f
    val settledSheetOffsetPx =
        when (sheetLevel) {
            SighListSheetLevel.Expanded -> 0f
            SighListSheetLevel.Middle -> middleOffsetPx
        }
    val targetSheetOffsetPx =
        if (isDraggingSheet) draggedSheetOffsetPx else settledSheetOffsetPx
    val animatedSheetOffsetPx by
        animateFloatAsState(
            targetValue = targetSheetOffsetPx,
            animationSpec = if (isDraggingSheet) snap() else tween(durationMillis = 260),
            label = "sigh-list-sheet-offset",
        )

    val expandThresholdPx = with(density) { 48.dp.toPx() }
    val collapseThresholdPx = with(density) { 28.dp.toPx() }
    val middleDismissThresholdPx = with(density) { 72.dp.toPx() }
    val expandedDismissThresholdPx =
        with(density) { 180.dp.toPx() }.coerceAtMost(availableHeightPx * 0.34f)

    val finishSheetDrag = {
        val dismiss =
            when (sheetLevel) {
                SighListSheetLevel.Middle -> {
                    if (totalDragPx <= -expandThresholdPx) {
                        sheetLevel = SighListSheetLevel.Expanded
                    }
                    totalDragPx >= middleDismissThresholdPx
                }

                SighListSheetLevel.Expanded -> {
                    if (totalDragPx >= collapseThresholdPx) {
                        sheetLevel = SighListSheetLevel.Middle
                    }
                    totalDragPx >= expandedDismissThresholdPx
                }
            }

        if (dismiss) {
            // 현재 손가락 위치에서 퇴장 애니메이션이 이어지도록 직접 조작 상태를 유지합니다.
            onDismissList()
        } else {
            isDraggingSheet = false
            totalDragPx = 0f
        }
    }
    val currentFinishSheetDrag = rememberUpdatedState(finishSheetDrag)
    val sheetHeight =
        if (compact) {
            Modifier.heightIn(min = 174.dp, max = 210.dp)
        } else {
            Modifier.fillMaxHeight()
        }
    val listBottomPadding =
        when {
            compact -> 12.dp
            sheetLevel == SighListSheetLevel.Middle -> 20.dp + with(density) { middleOffsetPx.toDp() }
            else -> 20.dp
        }
    val listState = rememberLazyListState()
    val sheetNestedScrollConnection =
        remember(listState, compact, availableHeightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (compact || source != NestedScrollSource.UserInput) return Offset.Zero

                    val isListAtTop =
                        listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0
                    val canStartDraggingSheet =
                        sheetLevel == SighListSheetLevel.Expanded &&
                            isListAtTop &&
                            available.y > 0f

                    if (!isListDraggingSheet && !canStartDraggingSheet) return Offset.Zero

                    if (!isListDraggingSheet) {
                        isListDraggingSheet = true
                        isDraggingSheet = true
                        draggedSheetOffsetPx = 0f
                        totalDragPx = 0f
                    }

                    val previousOffsetPx = draggedSheetOffsetPx
                    val nextOffsetPx =
                        (previousOffsetPx + available.y).coerceIn(0f, availableHeightPx)
                    val consumedY = nextOffsetPx - previousOffsetPx
                    draggedSheetOffsetPx = nextOffsetPx
                    totalDragPx += consumedY
                    return Offset(x = 0f, y = consumedY)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!isListDraggingSheet) return Velocity.Zero

                    isListDraggingSheet = false
                    currentFinishSheetDrag.value()
                    return Velocity(x = 0f, y = available.y)
                }
            }
        }

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
                .graphicsLayer {
                    translationY =
                        if (compact) compactDownwardDrag else animatedSheetOffsetPx
                }.pointerInput(compact, onCompactBackgroundClick) {
                    detectTapGestures {
                        if (compact) onCompactBackgroundClick()
                    }
                },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = AppColors.Navy800,
        contentColor = AppColors.Cream100,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .then(
                        if (!compact && sheetLevel == SighListSheetLevel.Expanded) {
                            Modifier.statusBarsPadding()
                        } else {
                            Modifier
                        },
                    ),
        ) {
            SighListHeader(
                compact = compact,
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                onDismissList = onDismissList,
                onCompactBackgroundClick = onCompactBackgroundClick,
                onDragStarted = {
                    if (compact) {
                        compactDownwardDrag = 0f
                    } else {
                        isListDraggingSheet = false
                        isDraggingSheet = true
                        draggedSheetOffsetPx = animatedSheetOffsetPx
                        totalDragPx = 0f
                    }
                },
                onVerticalDrag = { dragAmount ->
                    if (compact) {
                        if (dragAmount > 0f) compactDownwardDrag += dragAmount
                    } else {
                        totalDragPx += dragAmount
                        draggedSheetOffsetPx =
                            (draggedSheetOffsetPx + dragAmount).coerceIn(0f, availableHeightPx)
                    }
                },
                onDragFinished = {
                    if (compact) {
                        if (compactDownwardDrag >= middleDismissThresholdPx) onDismissList()
                        compactDownwardDrag = 0f
                    } else {
                        finishSheetDrag()
                    }
                },
                onDragCancelled = {
                    if (compact) {
                        compactDownwardDrag = 0f
                    } else {
                        isDraggingSheet = false
                        totalDragPx = 0f
                    }
                },
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(sheetNestedScrollConnection),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            top = 2.dp,
                            end = 16.dp,
                            bottom = listBottomPadding,
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
                                onLikeClick = { liked -> onLikeClick(item.id, liked) },
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
    onVerticalDrag: (Float) -> Unit,
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
                            if (!compact || dragAmount > 0f) {
                                change.consume()
                                onVerticalDrag(dragAmount)
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
                    .semantics {
                        contentDescription =
                            if (compact) {
                                "아래로 밀어 한숨 목록 닫기"
                            } else {
                                "위로 밀어 한숨 목록 펼치기, 아래로 밀어 축소하거나 닫기"
                            }
                    },
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
    onLikeClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmptyMemo = !item.hasMemo

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isEmptyMemo) AppColors.Navy800 else AppColors.Blue100.copy(alpha = 0.1f),
        contentColor = AppColors.Cream100,
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (isEmptyMemo) {
                        AppColors.Cream100.copy(alpha = 0.12f)
                    } else {
                        AppTheme.colors.outline
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapPinStar(color = item.starStage.toComposeStarColor(), modifier = Modifier.size(26.dp))
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
                    color =
                        if (isEmptyMemo) {
                            AppColors.Cream100.copy(alpha = 0.42f)
                        } else {
                            AppColors.Cream100
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier =
                    Modifier
                        .clickable(
                            onClick = {
                                onLikeClick(!item.liked)
                            },
                        ).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (item.liked) Res.drawable.ic_favorite else Res.drawable.ic_favorite_border,
                        ),
                    contentDescription = if (item.liked) "좋아요 취소" else "좋아요",
                    tint = if (item.liked) AppColors.Pink100 else AppTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.likeCount.toString(),
                    fontSize = 11.sp,
                    color = if (item.liked) AppColors.Pink100 else AppTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@Preview
private fun SighListItemPreview() {
    AppTheme {
        Surface(color = AppColors.Navy900) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SighListItem(
                    item =
                        SighListItemUiModel(
                            id = 1L,
                            nickname = "노래하는 고라니",
                            relativeTime = "12분 전",
                            memo = "아 개힘들다링 동동동동",
                            starStage = StarAgeStage.Fresh,
                            likeCount = 12L,
                        ),
                    onClick = {},
                    onLikeClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                SighListItem(
                    item =
                        SighListItemUiModel(
                            id = 2L,
                            nickname = "잠꾸러기 수달",
                            relativeTime = "3시간 전",
                            memo = "월요일이 왜 또 왔지",
                            starStage = StarAgeStage.Fresh,
                            liked = true,
                            likeCount = 128L,
                        ),
                    onClick = {},
                    onLikeClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                SighListItem(
                    item =
                        SighListItemUiModel(
                            id = 3L,
                            nickname = "졸린 수달",
                            relativeTime = "5시간 전",
                            memo = EMPTY_SIGH_MEMO,
                            hasMemo = false,
                            starStage = StarAgeStage.Fresh,
                            likeCount = 3L,
                        ),
                    onClick = {},
                    onLikeClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
