package com.pheeeew.feature.screens.collection.coverflow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.guide.COLLECTION_ISSUED_GUIDE_ANCHOR
import com.pheeeew.feature.guide.reportFeatureGuideAnchor
import com.pheeeew.feature.screens.collection.CollectionCarouselItem
import com.pheeeew.feature.screens.collection.CollectionCommonError
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import com.pheeeew.feature.screens.collection.CollectionScreenUiModel
import com.pheeeew.feature.screens.collection.CollectionSectionLoad
import com.pheeeew.feature.screens.collection.card.CollectionCard
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import com.pheeeew.feature.screens.collection.card.CollectionProgressCard
import com.pheeeew.feature.screens.collection.card.CollectionTomorrowCard
import com.pheeeew.feature.screens.collection.collectionPreviewCards
import com.pheeeew.feature.screens.collection.collectionPreviewPendingProgress
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_card_action_hint
import pheeeew.shared.generated.resources.collection_empty
import pheeeew.shared.generated.resources.collection_empty_hint
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import kotlin.math.absoluteValue

@Composable
internal fun CoverflowSection(
    uiModel: CollectionScreenUiModel,
    onClaim: (EmotionType) -> Unit,
    onRetryToday: () -> Unit,
    onLoadMore: () -> Unit,
    onCardClick: (CollectionCardUiModel) -> Unit,
    modifier: Modifier = Modifier,
    focusIssuedCardForGuide: Boolean = false,
) {
    val coverFlowItems = uiModel.coverFlowItems
    val todayCardIndex =
        uiModel.todayCardId?.let { todayCardId -> coverFlowItems.indexOfFirst { it.id == todayCardId } } ?: -1
    val pagerState = rememberPagerState { coverFlowItems.size.coerceAtLeast(1) }
    val unavailableState =
        CollectionUnavailableState.Waiting.takeIf {
            uiModel.commonError == CollectionCommonError.Initial ||
                uiModel.coverflowLoad !is CollectionSectionLoad.Content
        }
    var appliedScrollRequest by rememberSaveable { mutableStateOf<Long?>(null) }
    var appliedTodayCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedTodayCardIndex by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(uiModel.scrollToTopRequestId, uiModel.todayCardId, todayCardIndex, focusIssuedCardForGuide) {
        if (appliedScrollRequest != uiModel.scrollToTopRequestId ||
            appliedTodayCardId != uiModel.todayCardId ||
            appliedTodayCardIndex != todayCardIndex
        ) {
            pagerState.scrollToPage(todayCardIndex.takeIf { it >= 0 } ?: 0)
            appliedScrollRequest = uiModel.scrollToTopRequestId
            appliedTodayCardId = uiModel.todayCardId
            appliedTodayCardIndex = todayCardIndex
        } else if (focusIssuedCardForGuide && todayCardIndex >= 0 &&
            (pagerState.currentPage != todayCardIndex || pagerState.isScrollInProgress)
        ) {
            pagerState.scrollToPage(todayCardIndex)
        }
    }

    CollectionCoverFlow(
        items = coverFlowItems,
        unavailableState = unavailableState,
        pagerState = pagerState,
        isLoadingToday = uiModel.todayLoading,
        todayError = null,
        onClaim = onClaim,
        onRetryToday = onRetryToday,
        hasNext = uiModel.hasNext && uiModel.appendError == null,
        isLoadingNext = uiModel.isLoadingNext,
        onLoadMore = onLoadMore,
        onCardClick = onCardClick,
        modifier = modifier,
        guideCardId = uiModel.todayCardId.takeIf { focusIssuedCardForGuide },
    )
}

@Composable
private fun CollectionContentPlaceholder(
    state: CollectionUnavailableState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CollectionUnavailableState.Waiting -> {
            Box(modifier.fillMaxSize())
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

internal fun Modifier.coverFlowItem(
    pagerState: PagerState,
    page: Int,
): Modifier =
    layout { measurable, constraints ->
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
    guideCardId: String? = null,
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
            // Reserve card space first; omit accessories that would be too small to read.
            val accessoryHeight = (maxHeight - 160.dp).coerceAtLeast(0.dp)
            val paginationHeight = minOf(56.dp, accessoryHeight).takeIf { it >= 8.dp } ?: 0.dp
            val actionHintHeight =
                minOf(32.dp, (accessoryHeight - paginationHeight) / 2).takeIf { it >= 32.dp } ?: 0.dp
            // Balance the hint below the card with equal space above it.
            val cardHeight = (maxHeight - paginationHeight - actionHintHeight * 2).coerceAtLeast(0.dp)
            val availableWidth = maxWidth
            val cardWidth = minOf(availableWidth * 0.76f, cardHeight * 0.75f)
            val actualCardHeight = cardWidth / 0.75f
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(Modifier.height(actionHintHeight))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
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
                        val animatedModifier = Modifier.fillMaxWidth().coverFlowItem(pagerState, page)
                        val cardModifier =
                            if (issuedCard != null) {
                                animatedModifier.clickable(role = Role.Button) { onCardClick(issuedCard) }
                            } else {
                                animatedModifier
                            }
                        if (issuedCard != null) {
                            val issuedCardModifier =
                                if (issuedCard.id == guideCardId && page == pagerState.settledPage &&
                                    !pagerState.isScrollInProgress
                                ) {
                                    cardModifier.reportFeatureGuideAnchor("collection", COLLECTION_ISSUED_GUIDE_ANCHOR)
                                } else {
                                    cardModifier
                                }
                            CollectionCard(uiModel = issuedCard, modifier = issuedCardModifier)
                        } else if (item.isTomorrow) {
                            CollectionTomorrowCard(modifier = animatedModifier)
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
                }
                val selectedIndex = collectionCardIndex(pagerState.currentPage, items.size)
                val selectedItem = items[selectedIndex]
                val selectedEmotion = selectedItem.card?.emotion ?: selectedItem.progress?.emotion
                Box(
                    modifier = Modifier.fillMaxWidth().height(actionHintHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedItem.card != null && actionHintHeight > 0.dp) {
                        Text(
                            text = stringResource(Res.string.collection_card_action_hint),
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                val visibleDotCount = minOf(items.size, 7)
                val firstVisibleDot =
                    (selectedIndex - visibleDotCount / 2).coerceIn(0, items.size - visibleDotCount)
                val lastVisibleDot = firstVisibleDot + visibleDotCount - 1
                if (paginationHeight > 0.dp) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(paginationHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
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
                                                selectedEmotion?.representativeColor ?: AppColors.Primary
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
    }
}

private fun collectionCardIndex(
    page: Int,
    cardCount: Int,
): Int = page.coerceIn(0, cardCount - 1)

@Composable
private fun CollectionEmptyState(modifier: Modifier = Modifier) {
    CollectionCenteredState(modifier) {
        Image(
            painter = painterResource(Res.drawable.ic_emotion_frustrated),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.collection_empty),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = AppColors.RankingContent,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(19.dp))
        Text(
            text = stringResource(Res.string.collection_empty_hint),
            color = AppColors.RankingSecondaryContent,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionCoverFlowComponentPreview() {
    CollectionPreviewTheme {
        val items =
            buildList {
                add(
                    CollectionCarouselItem(
                        "pending",
                        progress = collectionPreviewPendingProgress(),
                    ),
                )
                collectionPreviewCards()
                    .forEach { add(CollectionCarouselItem(it.id, card = it)) }
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

@Preview(name = "컬렉션 · 내일 카드 안내", showBackground = true)
@Composable
private fun CollectionCoverFlowTomorrowPreview() {
    CollectionPreviewTheme {
        val items =
            buildList {
                add(CollectionCarouselItem("tomorrow", isTomorrow = true))
                collectionPreviewCards()
                    .forEach { add(CollectionCarouselItem(it.id, card = it)) }
            }
        CollectionCoverFlow(
            items = items,
            unavailableState = null,
            pagerState = rememberPagerState(pageCount = { items.size }),
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

@Preview(widthDp = 390, heightDp = 640, showBackground = true)
@Composable
private fun CollectionEmptyStateComponentPreview() {
    CollectionPreviewTheme {
        CollectionEmptyState()
    }
}
