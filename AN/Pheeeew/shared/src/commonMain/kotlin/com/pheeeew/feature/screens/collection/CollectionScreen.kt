package com.pheeeew.feature.screens.collection

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.card.CollectionCard
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import com.pheeeew.feature.screens.collection.card.CollectionProgressCard
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_album
import pheeeew.shared.generated.resources.collection_card_count
import pheeeew.shared.generated.resources.collection_cover_flow
import pheeeew.shared.generated.resources.collection_empty
import pheeeew.shared.generated.resources.collection_empty_hint
import pheeeew.shared.generated.resources.collection_filter_all
import pheeeew.shared.generated.resources.collection_loading
import pheeeew.shared.generated.resources.collection_retry
import pheeeew.shared.generated.resources.collection_subtitle
import pheeeew.shared.generated.resources.collection_title
import pheeeew.shared.generated.resources.collection_unissued_label
import pheeeew.shared.generated.resources.collection_unissued_label_compact
import pheeeew.shared.generated.resources.ic_collection_album
import pheeeew.shared.generated.resources.ic_collection_cover_flow
import kotlin.math.absoluteValue

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CollectionScreen(
    uiModel: CollectionScreenUiModel,
    onSaveCard: (cardId: String, capture: suspend () -> ByteArray) -> Unit,
    onShareCard: (cardId: String, capture: suspend () -> ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    initialViewMode: CollectionViewMode = CollectionViewMode.COVER_FLOW,
    bottomBar: @Composable () -> Unit = {},
    onCancelCardCapture: (String) -> Unit = {},
    onClaim: (EmotionType) -> Unit = {},
    onRetryClaim: () -> Unit = {},
    onFilterChange: (EmotionType?) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRetryCards: () -> Unit = {},
    onRetryToday: () -> Unit = {},
    onDismissCard: () -> Unit = {},
    onDismissSaveCompletion: () -> Unit = {},
    onRetryCard: () -> Unit = {},
    onCardClick: (String) -> Unit = {},
) {
    var viewModeName by rememberSaveable { mutableStateOf(initialViewMode.name) }
    var selectedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val viewMode = CollectionViewMode.valueOf(viewModeName)
    val coverFlowItems =
        buildList {
            uiModel.pendingProgress?.let { add(CollectionCarouselItem(id = "pending", progress = it)) }
            uiModel.cards.forEach { card -> add(CollectionCarouselItem(id = card.id, card = card)) }
        }
    val pagerState = rememberPagerState { coverFlowItems.size.coerceAtLeast(1) }
    val albumStateHolder = rememberSaveableStateHolder()
    val pullToRefreshState = rememberPullToRefreshState()
    // Forward vertical drags to PullToRefreshBox even when the content only scrolls horizontally.
    val coverFlowDragState = rememberScrollableState { 0f }
    val hapticFeedback = LocalHapticFeedback.current
    val pullDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val unavailableState =
        collectionUnavailableState(
            hasContent = uiModel.cards.isNotEmpty() || uiModel.pendingProgress != null,
            isLoading = uiModel.isLoadingCards || uiModel.todayLoading || uiModel.isRefreshRequestActive,
            cardsError = uiModel.cardsError,
            todayError = uiModel.todayError,
            onRetryCards = onRetryCards,
            onRetryToday = onRetryToday,
        )
    val visibleCards = uiModel.cards
    LaunchedEffect(visibleCards, selectedCardId) {
        if (selectedCardId != null && visibleCards.none { it.id == selectedCardId }) {
            selectedCardId = null
        }
    }
    LaunchedEffect(uiModel.scrollToTopRequestId) {
        pagerState.scrollToPage(0)
    }

    Column(
        modifier = modifier.fillMaxSize().background(AppColors.GroupBackground).statusBarsPadding(),
    ) {
        PullToRefreshBox(
            isRefreshing = uiModel.isRefreshIndicatorVisible,
            onRefresh = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                onRefresh()
            },
            state = pullToRefreshState,
            enabled = !uiModel.isRefreshRequestActive,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            indicator = {
                if (pullToRefreshState.distanceFraction >= 0.3f && !uiModel.isRefreshRequestActive) {
                    Text(
                        text = "당겨서 새로고침",
                        color = AppColors.TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = pullToRefreshState.distanceFraction.coerceIn(0f, 1f) * pullDistance
                        }.scrollable(
                            state = coverFlowDragState,
                            orientation = Orientation.Vertical,
                            enabled = viewMode == CollectionViewMode.COVER_FLOW && !uiModel.isRefreshRequestActive,
                        ),
                ) {
                    CollectionScreenHeader(
                        nickname = uiModel.nickname,
                        cardCount = uiModel.totalCount,
                        viewMode = viewMode,
                        onViewModeChange = {
                            viewModeName = it.name
                            if (it == CollectionViewMode.COVER_FLOW && uiModel.selectedFilter != null) {
                                onFilterChange(null)
                            }
                        },
                    )
                    uiModel.claimError?.let { error ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (uiModel.claimOutcomeUnknown) "$error 같은 요청으로 다시 확인할 수 있어요." else error,
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetryClaim) {
                                Text(stringResource(Res.string.collection_retry))
                            }
                        }
                    }
                    if (viewMode == CollectionViewMode.COVER_FLOW) {
                        CollectionCoverFlow(
                            items = coverFlowItems,
                            unavailableState = unavailableState,
                            pagerState = pagerState,
                            isLoadingToday = uiModel.todayLoading,
                            todayError = uiModel.todayError,
                            onClaim = onClaim,
                            onRetryToday = onRetryToday,
                            hasNext = uiModel.hasNext,
                            isLoadingNext = uiModel.isLoadingNext,
                            onLoadMore = onLoadMore,
                            onCardClick = { card ->
                                selectedCardId = card.id
                                onCardClick(card.id)
                            },
                            modifier = Modifier.weight(1f).padding(top = 20.dp),
                        )
                    } else {
                        CollectionAlbumSectionWithSavedState(
                            stateHolder = albumStateHolder,
                            uiModel = uiModel,
                            onFilterChange = onFilterChange,
                            onClaim = onClaim,
                            onRetryToday = onRetryToday,
                            onRetryCards = onRetryCards,
                            onLoadMore = onLoadMore,
                            onCardClick = { card ->
                                selectedCardId = card.id
                                onCardClick(card.id)
                            },
                        )
                    }
                }
                if (uiModel.isRefreshIndicatorVisible) {
                    CircularLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AppColors.Primary,
                    )
                }
            }
        }
        bottomBar()
    }

    selectedCardId?.let { cardId ->
        (uiModel.selectedCard?.takeIf { it.id == cardId } ?: visibleCards.firstOrNull { it.id == cardId })
            ?.let { selectedCard ->
                CollectionCardDialog(
                    card = selectedCard,
                    isLoading = uiModel.isLoadingCard,
                    error = uiModel.cardError,
                    isSaveCompleted = uiModel.saveCompletionCardId == selectedCard.id,
                    saveCompletionId = uiModel.saveCompletionId,
                    imageActionPhase = uiModel.imageActionPhase,
                    onDismissSaveCompletion = onDismissSaveCompletion,
                    onDismiss = {
                        onDismissSaveCompletion()
                        selectedCardId = null
                        onDismissCard()
                    },
                    onRetry = onRetryCard,
                    onSave = { capture -> onSaveCard(selectedCard.id, capture) },
                    onShare = { capture -> onShareCard(selectedCard.id, capture) },
                    onCancelCapture = onCancelCardCapture,
                )
            }
    }
}

@Composable
private fun ColumnScope.CollectionAlbumSectionWithSavedState(
    stateHolder: SaveableStateHolder,
    uiModel: CollectionScreenUiModel,
    onFilterChange: (EmotionType?) -> Unit,
    onClaim: (EmotionType) -> Unit,
    onRetryToday: () -> Unit,
    onRetryCards: () -> Unit,
    onLoadMore: () -> Unit,
    onCardClick: (CollectionCardUiModel) -> Unit,
) {
    val filterKey = uiModel.selectedFilter?.name ?: "all"
    CollectionEmotionFilters(
        selected = uiModel.selectedFilter,
        counts = uiModel.emotionCounts,
        onFilterChange = onFilterChange,
    )
    stateHolder.SaveableStateProvider(filterKey) {
        val albumState = rememberLazyGridState()
        LaunchedEffect(uiModel.scrollToTopRequestId) { albumState.scrollToItem(0) }
        CollectionAlbumSection(
            uiModel = uiModel,
            albumState = albumState,
            onClaim = onClaim,
            onRetryToday = onRetryToday,
            onRetryCards = onRetryCards,
            onLoadMore = onLoadMore,
            onCardClick = onCardClick,
        )
    }
}

private sealed interface CollectionUnavailableState {
    data object Waiting : CollectionUnavailableState

    data class Error(
        val message: String,
        val onRetry: () -> Unit,
    ) : CollectionUnavailableState

    data object Empty : CollectionUnavailableState
}

private fun collectionUnavailableState(
    hasContent: Boolean,
    isLoading: Boolean,
    cardsError: String?,
    todayError: String?,
    onRetryCards: () -> Unit,
    onRetryToday: () -> Unit,
): CollectionUnavailableState? =
    when {
        hasContent -> null
        isLoading -> CollectionUnavailableState.Waiting
        cardsError != null -> CollectionUnavailableState.Error(cardsError, onRetryCards)
        todayError != null -> CollectionUnavailableState.Error(todayError, onRetryToday)
        else -> CollectionUnavailableState.Empty
    }

@Composable
private fun ColumnScope.CollectionAlbumSection(
    uiModel: CollectionScreenUiModel,
    albumState: LazyGridState,
    onClaim: (EmotionType) -> Unit,
    onRetryToday: () -> Unit,
    onRetryCards: () -> Unit,
    onLoadMore: () -> Unit,
    onCardClick: (CollectionCardUiModel) -> Unit,
) {
    val pendingProgress = uiModel.pendingProgress.takeIf { uiModel.selectedFilter == null }
    val unavailableState =
        collectionUnavailableState(
            hasContent = uiModel.cards.isNotEmpty() || pendingProgress != null,
            isLoading =
                uiModel.isLoadingCards || uiModel.isRefreshRequestActive ||
                    (pendingProgress != null && uiModel.todayLoading),
            cardsError = uiModel.cardsError,
            todayError = uiModel.todayError.takeIf { pendingProgress != null },
            onRetryCards = onRetryCards,
            onRetryToday = onRetryToday,
        )
    if (unavailableState == null) {
        CollectionAlbum(
            cards = uiModel.cards,
            pendingProgress = pendingProgress,
            isLoadingToday = uiModel.todayLoading,
            todayError = uiModel.todayError,
            onClaim = onClaim,
            onRetryToday = onRetryToday,
            state = albumState,
            onCardClick = onCardClick,
            onLoadMore = onLoadMore,
            modifier = Modifier.weight(1f),
        )
    } else {
        CollectionContentPlaceholder(unavailableState, Modifier.weight(1f))
    }
    when {
        uiModel.isLoadingNext -> {
            Text(
                stringResource(Res.string.collection_loading),
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
            )
        }

        uiModel.cardsError != null && (uiModel.cards.isNotEmpty() || pendingProgress != null) -> {
            TextButton(onClick = onRetryCards, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(Res.string.collection_retry))
            }
        }
    }
}

@Composable
private fun CollectionContentPlaceholder(
    state: CollectionUnavailableState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CollectionUnavailableState.Waiting -> Box(modifier.fillMaxSize())

        is CollectionUnavailableState.Error -> {
            CollectionCenteredState(modifier) {
                Text(state.message, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                TextButton(onClick = state.onRetry) { Text(stringResource(Res.string.collection_retry)) }
            }
        }

        CollectionUnavailableState.Empty -> {
            CollectionEmptyState(modifier)
        }
    }
}

@Composable
private fun CollectionCenteredState(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { content() },
    )
}

@Composable
private fun CollectionEmotionFilters(
    selected: EmotionType?,
    counts: Map<EmotionType, Long>,
    onFilterChange: (EmotionType?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            CollectionFilterChip(
                selected == null,
                stringResource(Res.string.collection_filter_all),
            ) { onFilterChange(null) }
        }
        items(EmotionType.entries, key = { it.name }) { emotion ->
            val count = counts[emotion] ?: 0L
            CollectionFilterChip(selected == emotion, "${stringResource(emotion.label)} $count") {
                onFilterChange(emotion)
            }
        }
    }
}

@Composable
private fun CollectionFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val textColor = if (selected) AppColors.TextPrimary else AppColors.TextSecondary
    val borderColor = if (selected) AppColors.TextPrimary else AppColors.CollectionCardDivider
    Text(
        text = label,
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (selected) AppColors.Primary else AppColors.Surface)
                .border(1.dp, borderColor, CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun CollectionScreenHeader(
    nickname: String,
    cardCount: Long,
    viewMode: CollectionViewMode,
    onViewModeChange: (CollectionViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.collection_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.GroupInk,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.collection_subtitle, nickname),
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = stringResource(Res.string.collection_card_count, cardCount.toString()),
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        CollectionViewModeToggle(
            selectedMode = viewMode,
            onModeChange = onViewModeChange,
            modifier = Modifier.align(Alignment.End),
        )

    }
}

@Composable
private fun CollectionViewModeToggle(
    selectedMode: CollectionViewMode,
    onModeChange: (CollectionViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemWidth = 80.dp
    val itemHeight = 34.dp
    val indicatorOffset by
    animateDpAsState(
        targetValue = itemWidth * selectedMode.ordinal,
        animationSpec =
            tween(
                durationMillis = 240,
                easing = CubicBezierEasing(0f, 0f, 0.33f, 1f),
            ),
        label = "collectionViewModeIndicatorOffset",
    )
    Box(
        modifier
            .selectableGroup()
            .clip(CircleShape)
            .background(AppColors.Surface, CircleShape)
            .border(1.5.dp, AppColors.GroupInk, CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .size(width = itemWidth, height = itemHeight)
                .clip(CircleShape)
                .background(AppColors.Primary)
                .border(1.5.dp, AppColors.GroupInk, CircleShape),
        )
        Row {
            CollectionViewMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                Row(
                    Modifier
                        .clip(CircleShape)
                        .width(itemWidth)
                        .height(itemHeight)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onModeChange(mode) })
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (mode == CollectionViewMode.COVER_FLOW) {
                                    Res.drawable.ic_collection_cover_flow
                                } else {
                                    Res.drawable.ic_collection_album
                                },
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified,
                    )
                    Text(
                        text = stringResource(
                            if (mode == CollectionViewMode.COVER_FLOW) Res.string.collection_cover_flow
                            else Res.string.collection_album,
                        ),
                        color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

private data class CollectionCarouselItem(
    val id: String,
    val card: CollectionCardUiModel? = null,
    val progress: CollectionProgressUiModel? = null,
)

@Composable
private fun CollectionCoverFlow(
    items: List<CollectionCarouselItem>,
    unavailableState: CollectionUnavailableState?,
    pagerState: PagerState,
    isLoadingToday: Boolean,
    todayError: String?,
    onClaim: (EmotionType) -> Unit,
    onRetryToday: () -> Unit,
    hasNext: Boolean,
    isLoadingNext: Boolean,
    onLoadMore: () -> Unit,
    onCardClick: (CollectionCardUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(pagerState, items.size, hasNext, isLoadingNext) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (hasNext && !isLoadingNext && items.isNotEmpty() && page >= items.lastIndex - 2) {
                latestLoadMore()
            }
        }
    }
    if (items.isEmpty()) {
        CollectionContentPlaceholder(
            unavailableState ?: CollectionUnavailableState.Empty,
            modifier.fillMaxSize(),
        )
        return
    }
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val paginationHeight = 48.dp
            val cardHeight = (maxHeight - paginationHeight).coerceAtLeast(0.dp)
            val availableWidth = maxWidth
            val cardWidth = minOf(availableWidth * 0.76f, cardHeight * 0.75f)
            val actualCardHeight = cardWidth / 0.75f
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(actualCardHeight),
                    pageSize = PageSize.Fixed(cardWidth),
                    contentPadding = PaddingValues(horizontal = (availableWidth - cardWidth) / 2),
                    pageSpacing = availableWidth * 0.04f,
                    key = { page -> items.getOrNull(page)?.id ?: "stale-page-$page" },
                ) { page ->
                    val item = items.getOrNull(page) ?: return@HorizontalPager
                    val issuedCard = item.card
                    val progress = item.progress
                    val animatedModifier =
                        Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    val distance = offset.absoluteValue.coerceIn(0f, 1f)
                                    placeable.place(0, 0, zIndex = 1f - distance)
                                }
                            }.graphicsLayer {
                                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                val distance = offset.absoluteValue.coerceIn(0f, 1f)
                                scaleX = 1f - distance * 0.12f
                                scaleY = 1f - distance * 0.1f
                                rotationY = offset.coerceIn(-1f, 1f) * -12f
                                alpha = 1f - distance * 0.35f
                                cameraDistance = 12f * density
                            }
                    val cardModifier =
                        if (issuedCard != null) {
                            animatedModifier.clickable(role = Role.Button) { onCardClick(issuedCard) }
                        } else {
                            animatedModifier
                        }
                    if (issuedCard != null) {
                        CollectionCard(uiModel = issuedCard, modifier = cardModifier)
                    } else {
                        val unissuedProgress = requireNotNull(progress)
                        CollectionProgressCard(
                            progress = unissuedProgress,
                            isLoading = isLoadingToday,
                            error = todayError,
                            onClaim = { onClaim(unissuedProgress.emotion) },
                            onRetry = onRetryToday,
                            modifier = cardModifier,
                        )
                    }
                }
                val selectedIndex = collectionCardIndex(pagerState.currentPage, items.size)
                val selectedItem = items[selectedIndex]
                val selectedEmotion = selectedItem.card?.emotion ?: selectedItem.progress?.emotion
                val visibleDotCount = minOf(items.size, 7)
                val firstVisibleDot =
                    (selectedIndex - visibleDotCount / 2).coerceIn(0, items.size - visibleDotCount)
                val lastVisibleDot = firstVisibleDot + visibleDotCount - 1
                Text(
                    text =
                        if (selectedItem.progress != null) {
                            stringResource(Res.string.collection_unissued_label_compact)
                        } else {
                            stringResource(requireNotNull(selectedEmotion).label)
                        },
                    color = selectedEmotion?.representativeColor ?: AppColors.TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (index in firstVisibleDot..lastVisibleDot) {
                        val isTruncatedEdge =
                            (index == firstVisibleDot && firstVisibleDot > 0) ||
                                (index == lastVisibleDot && lastVisibleDot < items.lastIndex)
                        Box(
                            Modifier
                                .size(if (isTruncatedEdge) 4.dp else 6.dp)
                                .background(
                                    if (index == selectedIndex) {
                                        selectedEmotion?.representativeColor ?: AppColors.TextSecondary
                                    } else {
                                        AppColors.Gray100
                                    },
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private fun collectionCardIndex(
    page: Int,
    cardCount: Int,
): Int = page.coerceIn(0, cardCount - 1)

@Composable
private fun CollectionAlbum(
    cards: List<CollectionCardUiModel>,
    pendingProgress: CollectionProgressUiModel?,
    isLoadingToday: Boolean,
    todayError: String?,
    onClaim: (EmotionType) -> Unit,
    onRetryToday: () -> Unit,
    state: LazyGridState,
    onCardClick: (CollectionCardUiModel) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, cards.size) {
        snapshotFlow {
            state.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { index -> if (index != null && index >= cards.lastIndex - 3) latestLoadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        modifier =
            modifier.fillMaxSize().drawWithContent {
                drawContent()
                if (state.canScrollBackward) {
                    val fadeHeight = 24.dp.toPx().coerceAtMost(size.height)
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(AppColors.GroupBackground, Color.Transparent),
                                startY = 0f,
                                endY = fadeHeight,
                            ),
                        size = Size(size.width, fadeHeight),
                    )
                }
            },
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (pendingProgress != null) {
            item(key = "pending") {
                Column {
                    CollectionProgressCard(
                        progress = pendingProgress,
                        isLoading = isLoadingToday,
                        error = todayError,
                        onClaim = { onClaim(pendingProgress.emotion) },
                        onRetry = onRetryToday,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.collection_unissued_label),
                            color = pendingProgress.emotion.representativeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        items(cards, key = { it.id }) { card ->
            Column(
                Modifier
                    .clickable(role = Role.Button) { onCardClick(card) },
            ) {
                CollectionCard(uiModel = card, modifier = Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(card.emotion.label),
                        color = card.emotion.representativeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = card.period, color = AppColors.TextSecondary, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun CollectionEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.collection_empty),
            fontWeight = FontWeight.Bold,
            color = AppColors.GroupInk,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.collection_empty_hint),
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionScreenHeaderComponentPreview() {
    CollectionPreviewTheme {
        CollectionScreenHeader(
            nickname = "오늘도히유",
            cardCount = 5,
            viewMode = CollectionViewMode.COVER_FLOW,
            onViewModeChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionCoverFlowComponentPreview() {
    CollectionPreviewTheme {
        val items =
            buildList {
                add(CollectionCarouselItem("pending", progress = collectionPreviewPendingProgress()))
                collectionPreviewCards().forEach { add(CollectionCarouselItem(it.id, card = it)) }
            }
        CollectionCoverFlow(
            items = items,
            unavailableState = null,
            pagerState =
                rememberPagerState(
                    initialPage = 1,
                    pageCount = { items.size },
                ),
            isLoadingToday = false,
            todayError = null,
            onClaim = {},
            onRetryToday = {},
            hasNext = false,
            isLoadingNext = false,
            onLoadMore = {},
            onCardClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionAlbumComponentPreview() {
    CollectionPreviewTheme {
        CollectionAlbum(
            cards = collectionPreviewCards(),
            pendingProgress = collectionPreviewPendingProgress(),
            isLoadingToday = false,
            todayError = null,
            onClaim = {},
            onRetryToday = {},
            state = rememberLazyGridState(),
            onCardClick = {},
            onLoadMore = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 640, showBackground = true)
@Composable
private fun CollectionEmptyStateComponentPreview() {
    CollectionPreviewTheme {
        CollectionEmptyState()
    }
}

@Preview(name = "컬렉션 · 커버플로", showBackground = true)
@Composable
private fun CollectionScreenPreview() {
    CollectionPreview(CollectionViewMode.COVER_FLOW)
}

@Preview(name = "컬렉션 · 앨범", showBackground = true)
@Composable
private fun CollectionAlbumPreview() {
    CollectionPreview(CollectionViewMode.ALBUM)
}

@Preview(name = "컬렉션 · 작은 화면", showBackground = true)
@Composable
private fun CollectionCompactPreview() {
    CollectionPreview(CollectionViewMode.COVER_FLOW)
}

@Preview(name = "컬렉션 · 빈 목록", showBackground = true)
@Composable
private fun CollectionEmptyPreview() {
    CollectionPreviewTheme {
        CollectionScreen(
            uiModel = CollectionScreenUiModel(nickname = "오늘도히유", cards = emptyList()),
            onSaveCard = { _, _ -> },
            onShareCard = { _, _ -> },
        )
    }
}

@Composable
private fun CollectionPreview(mode: CollectionViewMode) {
    val cards = collectionPreviewCards()
    CollectionPreviewTheme {
        CollectionScreen(
            uiModel =
                CollectionScreenUiModel(
                    nickname = "오늘도히유",
                    cards = cards,
                    pendingProgress = collectionPreviewPendingProgress(),
                ),
            onSaveCard = { _, _ -> },
            onShareCard = { _, _ -> },
            initialViewMode = mode,
        )
    }
}

private fun collectionPreviewPendingProgress() =
    CollectionProgressUiModel(
        emotion = EmotionType.FRUSTRATED,
        stampCount = 3,
        stampTarget = 1,
        stampRemaining = 0,
        pressCount = 100,
        pressTarget = 100,
        pressRemaining = 0,
        canClaim = true,
        stampProgressByEmotion =
            listOf(
                CollectionEmotionProgress(EmotionType.FRUSTRATED, 1),
                CollectionEmotionProgress(EmotionType.IRRITATED, 1),
                CollectionEmotionProgress(EmotionType.EXHAUSTED, 1),
                CollectionEmotionProgress(EmotionType.DISCOURAGED, 0),
                CollectionEmotionProgress(EmotionType.ANGRY, 0),
            ),
        pressProgressByEmotion =
            listOf(
                CollectionEmotionProgress(EmotionType.FRUSTRATED, 30),
                CollectionEmotionProgress(EmotionType.IRRITATED, 25),
                CollectionEmotionProgress(EmotionType.EXHAUSTED, 20),
                CollectionEmotionProgress(EmotionType.DISCOURAGED, 15),
                CollectionEmotionProgress(EmotionType.ANGRY, 10),
            ),
    )

@Composable
private fun collectionPreviewCards(): List<CollectionCardUiModel> {
    val emotions =
        listOf(
            EmotionType.EXHAUSTED,
            EmotionType.FRUSTRATED,
            EmotionType.IRRITATED,
            EmotionType.DISCOURAGED,
            EmotionType.ANGRY,
        )
    return emotions.map { emotion ->
        val label = stringResource(emotion.label)
        CollectionCardUiModel(
            id = emotion.name,
            period = "2026.10.18",
            title = "오늘도히유",
            emotion = emotion,
            pressLabel = "$label 프레스",
            pressCount = 312,
            stampLabel = "$label 스탬프",
            stampCount = 23,
            characterImageUrl = Res.getUri("drawable/tmp_emotionimg.png"),
            textStampImageUrl = Res.getUri("drawable/tmp_text_stamp.png"),
            roundStampImageUrl = Res.getUri("drawable/tmp_round_stamp.png"),
        )
    }
}

/** Preview asset의 스트림 디코딩 오류를 피하도록 임시 PNG를 바이트로 읽는다. */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun CollectionPreviewTheme(content: @Composable () -> Unit) {
    ConfigureCollectionPreviewResources()
    val previewHandler =
        remember {
            val resourcePaths =
                listOf(
                    "drawable/tmp_emotionimg.png",
                    "drawable/tmp_text_stamp.png",
                    "drawable/tmp_round_stamp.png",
                )
            AsyncImagePreviewHandler { imageLoader, request ->
                val path = resourcePaths.firstOrNull { Res.getUri(it) == request.data }
                val previewRequest =
                    if (path != null) {
                        request.newBuilder().data(Res.readBytes(path)).build()
                    } else {
                        request
                    }
                AsyncImagePreviewHandler.Default.handle(imageLoader, previewRequest)
            }
        }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewHandler) {
        AppTheme { content() }
    }
}

@Composable
internal expect fun ConfigureCollectionPreviewResources()
