package com.pheeeew.feature.screens.collection.calendar.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.audio.AudioPlaybackState
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionDayEmotionContent
import com.pheeeew.feature.screens.collection.CollectionDayEmotionUiModel
import com.pheeeew.feature.screens.collection.CollectionDayPageUiModel
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import com.pheeeew.feature.screens.collection.CollectionProgressUiModel
import com.pheeeew.feature.screens.collection.card.CollectionCard
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import com.pheeeew.feature.screens.collection.card.CollectionProgressCard
import com.pheeeew.feature.screens.collection.card.CollectionUnissuedCard
import com.pheeeew.feature.screens.collection.coverflow.coverFlowItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_card_action_hint
import pheeeew.shared.generated.resources.collection_day_cards_error
import pheeeew.shared.generated.resources.ic_arrow_back
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_frustrated

@Composable
internal fun CollectionDayDetailScreen(
    pages: List<CollectionDayPageUiModel>,
    selectedCardId: String,
    nickname: String,
    emotionContent: CollectionDayEmotionContent,
    emotionCount: Int?,
    audio: CollectionDayAudioState,
    onBack: () -> Unit,
    onCardSettled: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    onLoadMoreEmotions: () -> Unit,
    onRetryEmotions: () -> Unit,
    onDeleteEmotion: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    onRetryNextEmotions: () -> Unit = onRetryEmotions,
    todayDate: String? = null,
    todayProgress: CollectionProgressUiModel? = null,
    isLoadingToday: Boolean = false,
    isLoadingCalendar: Boolean = false,
    todayClaimError: String? = null,
    onClaimToday: (EmotionType) -> Unit = {},
    onRetryTodayClaim: () -> Unit = {},
    onReactToEmotion: (Long, EmotionReactionType) -> Unit = { _, _ -> },
    pendingReactionIds: Set<Long> = emptySet(),
    reactionError: String? = null,
    cardLoadError: String? = null,
    isCommonError: Boolean = false,
    onRetryCardLoad: () -> Unit = {},
) {
    val dates = remember(pages) { pages.map { it.date } }
    val initialPage = dates.indexOf(selectedCardId).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { dates.size.coerceAtLeast(1) })
    val latestCardSettled by rememberUpdatedState(onCardSettled)
    val listState = rememberLazyListState()

    fun canInteractWithEmotions(): Boolean =
        !pagerState.isScrollInProgress && dates.getOrNull(pagerState.settledPage) == selectedCardId

    LaunchedEffect(selectedCardId) { listState.scrollToItem(0) }
    LaunchedEffect(pagerState, dates) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page -> dates.getOrNull(page)?.let { latestCardSettled(it) } }
    }
    val emotionItems = emotionContent as? CollectionDayEmotionContent.Items
    LaunchedEffect(listState, selectedCardId, emotionItems?.items?.size, emotionItems?.canLoadMore) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .collect { index ->
                if (emotionItems != null && emotionItems.canLoadMore && index != null &&
                    index >= emotionItems.items.size
                ) {
                    onLoadMoreEmotions()
                }
            }
    }

    Column(
        modifier.fillMaxSize().background(AppColors.GroupBackground).navigationBarsPadding(),
    ) {
        val selectedPage = pages.firstOrNull { it.date == selectedCardId }
        CollectionDayHeader(title = selectedPage?.title, onBack = onBack)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "card-header") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    selectedPage?.let {
                        Text(
                            text = it.subtitle,
                            modifier = Modifier.padding(bottom = 16.dp),
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardWidth = maxWidth * 0.65f
                        val cardHeight = cardWidth / 0.75f
                        if (cardLoadError != null) {
                            EmotionListLoadError(
                                title = stringResource(Res.string.collection_day_cards_error),
                                message = cardLoadError,
                                hasItems = false,
                                icon =
                                    if (isCommonError) {
                                        Res.drawable.ic_emotion_frustrated
                                    } else {
                                        Res.drawable.ic_emotion_discouraged
                                    },
                                onRetry = onRetryCardLoad,
                                modifier = Modifier.fillMaxWidth().heightIn(min = cardHeight),
                            )
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth().height(cardHeight),
                                pageSize = PageSize.Fixed(cardWidth),
                                contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                                pageSpacing = maxWidth * 0.04f,
                                key = { index -> dates.getOrNull(index) ?: "empty-$index" },
                            ) { page ->
                                val pageModel = pages.getOrNull(page) ?: return@HorizontalPager
                                val card = pageModel.card
                                if (card != null) {
                                    CollectionCard(
                                        uiModel = card,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .coverFlowItem(pagerState, page)
                                                .clickable(role = Role.Button) { onOpenCard(card.id) },
                                    )
                                } else if (pageModel.date == todayDate && todayProgress != null) {
                                    CollectionProgressCard(
                                        progress = todayProgress,
                                        isLoading = isLoadingToday,
                                        error = todayClaimError,
                                        onClaim = { onClaimToday(todayProgress.emotion) },
                                        onRetry = onRetryTodayClaim,
                                        modifier = Modifier.fillMaxWidth().coverFlowItem(pagerState, page),
                                    )
                                } else if (isLoadingCalendar || (pageModel.date == todayDate && isLoadingToday)) {
                                    Box(
                                        Modifier.fillMaxSize().coverFlowItem(pagerState, page),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularLoadingIndicator(color = AppColors.Primary)
                                    }
                                } else {
                                    CollectionUnissuedCard(
                                        modifier = Modifier.fillMaxWidth().coverFlowItem(pagerState, page),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(Res.string.collection_card_action_hint),
                        modifier =
                            if (selectedPage?.card != null &&
                                cardLoadError == null
                            ) {
                                Modifier
                            } else {
                                Modifier.alpha(0f).clearAndSetSemantics {}
                            },
                        color = AppColors.TextSecondary,
                        fontSize = 10.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("내가 남긴 감정", color = AppColors.GroupInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        emotionCount?.takeIf { it > 0 }?.let {
                            Text(
                                "  $it",
                                color = AppColors.TextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("최신순", color = AppColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
            if (emotionContent != CollectionDayEmotionContent.Waiting) {
                collectionDayEmotionItems(
                    content = emotionContent,
                    nickname = nickname,
                    audio = audio,
                    onRetryEmotions = onRetryEmotions,
                    onDeleteEmotion = { if (canInteractWithEmotions()) onDeleteEmotion(it) },
                    onRetryNextEmotions = onRetryNextEmotions,
                    onReact = { id, type -> if (canInteractWithEmotions()) onReactToEmotion(id, type) },
                    pendingReactionIds = pendingReactionIds,
                )
                if (reactionError != null) {
                    item(key = "emotion-reaction-error") {
                        Text(
                            text = reactionError,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            color = AppColors.RecordSheetRecording,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CollectionDayHeader(
    title: String?,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = "달력으로 돌아가기",
                tint = AppColors.GroupInk,
            )
        }
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 44.dp),
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(name = "받은 날짜 상세", widthDp = 402, heightDp = 850, showBackground = true)
@Composable
private fun CollectionDayDetailScreenPreview() {
    CollectionPreviewTheme {
        CollectionDayDetailScreen(
            pages =
                listOf(
                    CollectionDayPageUiModel(
                        date = "2026-10-15",
                        title = "10월 15일",
                        subtitle = "2026년 · 목요일",
                        card =
                            CollectionCardUiModel(
                                id = "preview-card",
                                period = "2026.10.15",
                                title = "오늘도히유",
                                emotion = EmotionType.EXHAUSTED,
                                pressLabel = "프레스",
                                pressCount = 128,
                                stampLabel = "스탬프",
                                stampCount = 3,
                            ),
                    ),
                    CollectionDayPageUiModel(
                        date = "2026-10-14",
                        title = "10월 14일",
                        subtitle = "2026년 · 수요일",
                        card =
                            CollectionCardUiModel(
                                id = "preview-card-next",
                                period = "2026.10.14",
                                title = "오늘도히유",
                                emotion = EmotionType.EXHAUSTED,
                                pressLabel = "프레스",
                                pressCount = 96,
                                stampLabel = "스탬프",
                                stampCount = 2,
                            ),
                    ),
                ),
            selectedCardId = "2026-10-15",
            nickname = "오늘도히유",
            emotionContent =
                CollectionDayEmotionContent.Items(
                    listOf(
                        CollectionDayEmotionUiModel(
                            id = 3,
                            timeLabel = "방금 전",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 2,
                            timeLabel = "방금 전",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 4,
                            timeLabel = "방금 전",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 5,
                            timeLabel = "방금 전",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                        CollectionDayEmotionUiModel(
                            id = 1,
                            timeLabel = "방금 전",
                            emotion = EmotionType.EXHAUSTED,
                            contentType = CollectionEmotionContentType.MEMO,
                            memo = "오늘은 여기까지만.",
                            playbackUrl = null,
                            playbackExpiresAt = null,
                        ),
                    ),
                ),
            emotionCount = 1,
            audio =
                CollectionDayAudioState(
                    null,
                    remember { MutableStateFlow(AudioPlaybackState.Empty) },
                    false,
                    null,
                ) {},
            onBack = {},
            onCardSettled = {},
            onOpenCard = {},
            onLoadMoreEmotions = {},
            onRetryEmotions = {},
        )
    }
}

@Preview(name = "날짜 상세 · 전체 오류", widthDp = 402, heightDp = 850, showBackground = true)
@Composable
private fun CollectionDayCommonErrorPreview() {
    CollectionPreviewTheme {
        Column(Modifier.fillMaxSize().background(AppColors.GroupBackground)) {
            CollectionDayHeader(title = "10월 15일", onBack = {})
            EmotionListLoadError(
                title = stringResource(Res.string.collection_day_cards_error),
                message = "인터넷 연결을 확인하고 다시 시도해 주세요.",
                hasItems = false,
                icon = Res.drawable.ic_emotion_frustrated,
                onRetry = {},
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "날짜 상세 · 카드 오류", widthDp = 402, heightDp = 850, showBackground = true)
@Composable
private fun CollectionDayCardErrorPreview() {
    CollectionDayFeedbackPreview(cardError = "이번 달 카드를 불러오지 못했어요.")
}

@Preview(name = "날짜 상세 · 감정 오류", widthDp = 402, heightDp = 850, showBackground = true)
@Composable
private fun CollectionDayEmotionErrorPreview() {
    CollectionDayFeedbackPreview(emotionError = "서버 응답을 확인할 수 없어요.")
}

@Preview(name = "날짜 상세 · 남긴 감정 없음", widthDp = 402, heightDp = 850, showBackground = true)
@Composable
private fun CollectionDayEmptyEmotionsPreview() {
    CollectionDayFeedbackPreview()
}

@Composable
private fun CollectionDayFeedbackPreview(
    cardError: String? = null,
    emotionError: String? = null,
) {
    CollectionPreviewTheme {
        val date = "2026-10-15"
        val playback = remember { MutableStateFlow(AudioPlaybackState.Empty) }
        CollectionDayDetailScreen(
            pages = listOf(CollectionDayPageUiModel(date, "10월 15일", "2026년 · 목요일")),
            selectedCardId = date,
            nickname = "오늘도히유",
            emotionContent =
                when {
                    emotionError != null -> CollectionDayEmotionContent.Error(emotionError)
                    cardError != null -> CollectionDayEmotionContent.Waiting
                    else -> CollectionDayEmotionContent.Empty
                },
            emotionCount = 0.takeIf { cardError == null && emotionError == null },
            audio = CollectionDayAudioState(null, playback, false, null) {},
            cardLoadError = cardError,
            onBack = {},
            onCardSettled = {},
            onOpenCard = {},
            onLoadMoreEmotions = {},
            onRetryEmotions = {},
        )
    }
}
