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
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.collection.CollectionEmotionContentType
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionDayEmotionUiModel
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import com.pheeeew.feature.screens.collection.CollectionProgressUiModel
import com.pheeeew.feature.screens.collection.calendar.collectionDayOfWeek
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
import pheeeew.shared.generated.resources.ic_arrow_back

@Composable
internal fun CollectionDayDetailScreen(
    cards: List<CollectionCardUiModel>,
    selectedCardId: String,
    nickname: String,
    emotions: List<CollectionDayEmotionUiModel>,
    emotionCount: Int?,
    isLoadingEmotions: Boolean,
    isLoadingNextEmotions: Boolean,
    emotionsError: String?,
    hasNextEmotions: Boolean,
    audio: CollectionDayAudioState,
    onBack: () -> Unit,
    onCardSettled: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    onLoadMoreEmotions: () -> Unit,
    onRetryEmotions: () -> Unit,
    onDeleteEmotion: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    emotionsLoaded: Boolean = true,
    onRetryNextEmotions: () -> Unit = onRetryEmotions,
    dates: List<String> = cards.map { it.id },
    todayDate: String? = null,
    todayProgress: CollectionProgressUiModel? = null,
    isLoadingToday: Boolean = false,
    todayClaimError: String? = null,
    onClaimToday: (EmotionType) -> Unit = {},
    onRetryTodayClaim: () -> Unit = {},
    onReactToEmotion: (Long, EmotionReactionType) -> Unit = { _, _ -> },
    pendingReactionIds: Set<Long> = emptySet(),
    reactionError: String? = null,
) {
    val initialPage = dates.indexOf(selectedCardId).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { dates.size.coerceAtLeast(1) })
    val latestCardSettled by rememberUpdatedState(onCardSettled)
    val listState = rememberLazyListState()
    LaunchedEffect(selectedCardId) { listState.scrollToItem(0) }
    LaunchedEffect(pagerState, dates) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page -> dates.getOrNull(page)?.let { latestCardSettled(it) } }
    }
    LaunchedEffect(listState, emotions.size, isLoadingNextEmotions, hasNextEmotions, emotionsError) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.distinctUntilChanged()
            .collect { index ->
                if (hasNextEmotions && !isLoadingNextEmotions && emotionsError == null && index != null &&
                    index >= emotions.size
                ) {
                    onLoadMoreEmotions()
                }
            }
    }

    Column(
        modifier.fillMaxSize().background(AppColors.GroupBackground).navigationBarsPadding(),
    ) {
        val currentKey = dates.getOrNull(pagerState.currentPage) ?: selectedCardId
        val currentCard = cards.firstOrNull { it.id == currentKey || it.period.take(10).replace('.', '-') == currentKey }
        val date =
            (currentCard?.period ?: currentKey)
                .take(10)
                .replace('.', '-')
        val parts = date.split('-')
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        val day = parts.getOrNull(2)?.toIntOrNull()
        val hasDate =
            year != null && year in 1..9999 &&
                month != null && month in 1..12 && day != null && day in 1..31

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
            if (hasDate) {
                Text(
                    text = "${parts[1].padStart(2, '0')}월 ${parts[2].padStart(2, '0')}일",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 44.dp),
                    color = AppColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "card-header") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasDate) {
                        val normalizedDate = "${parts[0].padStart(
                            4,
                            '0',
                        )}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}"
                        Text(
                            text = "${year}년 · ${collectionDayOfWeek(normalizedDate)}",
                            modifier = Modifier.padding(bottom = 16.dp),
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardWidth = maxWidth * 0.65f
                        val cardHeight = cardWidth / 0.75f
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().height(cardHeight),
                            pageSize = PageSize.Fixed(cardWidth),
                            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                            pageSpacing = maxWidth * 0.04f,
                            key = { index -> dates.getOrNull(index) ?: "empty-$index" },
                        ) { page ->
                            val date = dates.getOrNull(page) ?: return@HorizontalPager
                            val card = cards.firstOrNull { it.id == date || it.period.take(10).replace('.', '-') == date }
                            if (card != null) {
                                CollectionCard(
                                    uiModel = card,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .coverFlowItem(pagerState, page)
                                            .clickable(role = Role.Button) { onOpenCard(card.id) },
                                )
                            } else if (date == todayDate && todayProgress != null) {
                                CollectionProgressCard(
                                    progress = todayProgress,
                                    isLoading = isLoadingToday,
                                    error = todayClaimError,
                                    onClaim = { onClaimToday(todayProgress.emotion) },
                                    onRetry = onRetryTodayClaim,
                                    modifier = Modifier.fillMaxWidth().coverFlowItem(pagerState, page),
                                )
                            } else {
                                CollectionUnissuedCard(
                                    modifier = Modifier.fillMaxWidth().coverFlowItem(pagerState, page),
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(Res.string.collection_card_action_hint),
                        modifier =
                            if (currentCard != null) Modifier else Modifier.alpha(0f).clearAndSetSemantics {},
                        color = AppColors.TextSecondary,
                        fontSize = 10.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("내가 남긴 감정", color = AppColors.GroupInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        emotionCount?.takeIf { currentKey == selectedCardId }?.let {
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
            if (currentKey == selectedCardId &&
                (emotionsLoaded || isLoadingEmotions || emotionsError != null || emotions.isNotEmpty())
            ) {
                collectionDayEmotionItems(
                    emotions = emotions,
                    nickname = nickname,
                    isLoadingEmotions = isLoadingEmotions,
                    isLoadingNextEmotions = isLoadingNextEmotions,
                    emotionsError = emotionsError,
                    audio = audio,
                    onRetryEmotions = onRetryEmotions,
                    onDeleteEmotion = onDeleteEmotion,
                    onRetryNextEmotions = onRetryNextEmotions,
                    onReact = onReactToEmotion,
                    pendingReactionIds = pendingReactionIds,
                )
                if (reactionError != null) item(key = "emotion-reaction-error") {
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

@Preview(name = "받은 날짜 상세", widthDp = 402, heightDp = 850, showBackground = true)
@Composable
private fun CollectionDayDetailScreenPreview() {
    CollectionPreviewTheme {
        CollectionDayDetailScreen(
            cards =
                listOf(
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
            selectedCardId = "preview-card",
            nickname = "오늘도히유",
            emotions =
                listOf(
                    CollectionDayEmotionUiModel(
                        id = 3,
                        createdAt = "2026-10-15T11:42:00Z",
                        emotion = EmotionType.EXHAUSTED,
                        contentType = CollectionEmotionContentType.MEMO,
                        memo = "오늘은 여기까지만.",
                        playbackUrl = null,
                        playbackExpiresAt = null,
                    ),
                    CollectionDayEmotionUiModel(
                        id = 2,
                        createdAt = "2026-10-15T11:42:00Z",
                        emotion = EmotionType.EXHAUSTED,
                        contentType = CollectionEmotionContentType.MEMO,
                        memo = "오늘은 여기까지만.",
                        playbackUrl = null,
                        playbackExpiresAt = null,
                    ),
                    CollectionDayEmotionUiModel(
                        id = 4,
                        createdAt = "2026-10-15T11:42:00Z",
                        emotion = EmotionType.EXHAUSTED,
                        contentType = CollectionEmotionContentType.MEMO,
                        memo = "오늘은 여기까지만.",
                        playbackUrl = null,
                        playbackExpiresAt = null,
                    ),
                    CollectionDayEmotionUiModel(
                        id = 5,
                        createdAt = "2026-10-15T11:42:00Z",
                        emotion = EmotionType.EXHAUSTED,
                        contentType = CollectionEmotionContentType.MEMO,
                        memo = "오늘은 여기까지만.",
                        playbackUrl = null,
                        playbackExpiresAt = null,
                    ),
                    CollectionDayEmotionUiModel(
                        id = 1,
                        createdAt = "2026-10-15T11:42:00Z",
                        emotion = EmotionType.EXHAUSTED,
                        contentType = CollectionEmotionContentType.MEMO,
                        memo = "오늘은 여기까지만.",
                        playbackUrl = null,
                        playbackExpiresAt = null,
                    ),
                ),
            emotionCount = 1,
            isLoadingEmotions = false,
            isLoadingNextEmotions = false,
            emotionsError = null,
            hasNextEmotions = false,
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
