package com.pheeeew.feature.screens.collection

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.guide.COLLECTION_CALENDAR_GUIDE_ANCHOR
import com.pheeeew.feature.guide.reportFeatureGuideAnchor
import com.pheeeew.feature.screens.collection.calendar.CalendarSection
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import com.pheeeew.feature.screens.collection.coverflow.CoverflowSection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_calendar
import pheeeew.shared.generated.resources.collection_card_count
import pheeeew.shared.generated.resources.collection_cover_flow
import pheeeew.shared.generated.resources.collection_notification_outdated
import pheeeew.shared.generated.resources.collection_subtitle
import pheeeew.shared.generated.resources.collection_title
import pheeeew.shared.generated.resources.ic_calendar
import pheeeew.shared.generated.resources.ic_collection_cover_flow

@Composable
fun CollectionScreen(
    uiModel: CollectionScreenUiModel,
    onSaveCard: (cardId: String, capture: suspend () -> ByteArray) -> Unit,
    onShareCard: (cardId: String, capture: suspend () -> ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    onCancelCardCapture: (String) -> Unit = {},
    onClaim: (EmotionType) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRetryToday: () -> Unit = {},
    onDismissCard: () -> Unit = {},
    onDismissSaveCompletion: () -> Unit = {},
    onRetryCard: () -> Unit = {},
    onCardClick: (String) -> Unit = {},
    onCalendarMonthChange: (String) -> Unit = {},
    onCalendarDayClick: (String) -> Unit = {},
    onCalendarModeChanged: (Boolean) -> Unit = {},
    focusIssuedCardForGuide: Boolean = false,
) {
    var viewMode by rememberSaveable { mutableStateOf(CollectionViewMode.COVER_FLOW) }
    LaunchedEffect(viewMode) {
        onCalendarModeChanged(viewMode == CollectionViewMode.ALBUM)
    }
    val sectionLoad =
        if (viewMode == CollectionViewMode.COVER_FLOW) uiModel.coverflowLoad else uiModel.calendarLoad
    val sectionFailure =
        when (sectionLoad) {
            is CollectionSectionLoad.InitialFailure -> sectionLoad.failure
            is CollectionSectionLoad.Content -> sectionLoad.refreshFailure
            CollectionSectionLoad.InitialLoading -> null
        }
    val claimError = uiModel.claimError
    val errorMessage =
        when {
            uiModel.commonError != null -> "인터넷 연결을 확인하고 다시 시도해 주세요."
            claimError != null -> claimError
            sectionFailure != null -> sectionFailure.message
            viewMode == CollectionViewMode.COVER_FLOW -> uiModel.appendError
            else -> null
        }
    var errorDismissed by remember(errorMessage, sectionFailure, uiModel.commonError) { mutableStateOf(false) }
    val tabState = rememberSaveableStateHolder()
    var selectedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val pullDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val scope = rememberCoroutineScope()
    val latestRefresh by rememberUpdatedState(onRefresh)
    val isPullEnabled by rememberUpdatedState(!uiModel.isRefreshRequestActive)
    val pullState =
        remember(scope, pullDistance, hapticFeedback) {
            CollectionPullRefreshState(scope, pullDistance, { isPullEnabled }) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                latestRefresh()
            }
        }
    val visibleCards = uiModel.cards
    LaunchedEffect(visibleCards, uiModel.calendarCards, selectedCardId) {
        if (selectedCardId != null && visibleCards.none { it.id == selectedCardId } &&
            uiModel.calendarCards.none { it.id == selectedCardId }
        ) {
            selectedCardId = null
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(AppColors.GroupBackground).statusBarsPadding(),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(pullState)
                    .pointerInput(pullState) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            pullState.beginGesture()
                            var released = false
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    released = event.changes.none { it.pressed }
                                } while (!released)
                            } finally {
                                pullState.release(cancelled = !released)
                            }
                        }
                    },
        ) {
            if (pullState.offset >= pullDistance * 0.3f && !uiModel.isRefreshRequestActive) {
                Text(
                    text = "당겨서 새로고침",
                    color = AppColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                tabState.SaveableStateProvider(viewMode.name) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = pullState.offset
                            }.scrollable(
                                state = rememberScrollableState { 0f },
                                orientation = Orientation.Vertical,
                                enabled = viewMode == CollectionViewMode.COVER_FLOW,
                            ),
                    ) {
                        CollectionScreenHeader(
                            nickname = uiModel.nickname,
                            cardCount = uiModel.totalCount,
                            viewMode = viewMode,
                            onViewModeChange = { viewMode = it },
                        )
                        if (uiModel.isNotificationDateOutdated) {
                            Text(
                                text = stringResource(Res.string.collection_notification_outdated),
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                        if (viewMode == CollectionViewMode.COVER_FLOW) {
                            CoverflowSection(
                                uiModel = uiModel,
                                focusIssuedCardForGuide = focusIssuedCardForGuide,
                                onClaim = onClaim,
                                onRetryToday = onRetryToday,
                                onLoadMore = onLoadMore,
                                onCardClick = { card ->
                                    selectedCardId = card.id
                                    onCardClick(card.id)
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        } else {
                            CalendarSection(
                                month = uiModel.calendarMonth,
                                todayDate = uiModel.todayDate,
                                cards = uiModel.calendarCards,
                                isLoading =
                                    uiModel.calendarLoad == CollectionSectionLoad.InitialLoading ||
                                        uiModel.isLoadingCalendar,
                                hasLoaded =
                                    uiModel.commonError != CollectionCommonError.Initial &&
                                        uiModel.calendarLoad is CollectionSectionLoad.Content,
                                showRefreshIndicator = !uiModel.isRefreshRequestActive,
                                onMonthChange = onCalendarMonthChange,
                                onDateClick = onCalendarDayClick,
                                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                if (uiModel.isRefreshIndicatorVisible) {
                    CircularLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AppColors.Primary,
                    )
                }
                Snackbar(
                    message = errorMessage.takeUnless { errorDismissed },
                    onDismiss = { errorDismissed = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp),
                    isError = true,
                    maxLines = 3,
                )
            }
        }
        bottomBar()
    }

    selectedCardId?.let { cardId ->
        (
            uiModel.selectedCard?.takeIf { it.id == cardId }
                ?: visibleCards.firstOrNull { it.id == cardId } ?: uiModel.calendarCards.firstOrNull { it.id == cardId }
        )?.let { selectedCard ->
            CollectionCardDialog(
                card = selectedCard,
                isLoading = uiModel.isLoadingCard,
                error = uiModel.cardError,
                isSaveCompleted = uiModel.saveCompletionCardId == selectedCard.id,
                saveCompletionId = uiModel.saveCompletionId,
                imageActionError = uiModel.imageActionError,
                imageActionErrorCardId = uiModel.imageActionErrorCardId,
                imageActionErrorId = uiModel.imageActionErrorId,
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
private fun CollectionScreenHeader(
    nickname: String,
    cardCount: Long,
    viewMode: CollectionViewMode = CollectionViewMode.COVER_FLOW,
    onViewModeChange: (CollectionViewMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
    ) {
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.collection_card_count, cardCount.toString()),
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
            )
            CollectionViewModeToggle(
                selectedMode = viewMode,
                onModeChange = onViewModeChange,
                modifier = Modifier.reportFeatureGuideAnchor("collection", COLLECTION_CALENDAR_GUIDE_ANCHOR),
            )
        }
    }
}

private enum class CollectionViewMode { COVER_FLOW, ALBUM }

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
                                    Res.drawable.ic_calendar
                                },
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified,
                    )
                    Text(
                        text =
                            stringResource(
                                if (mode == CollectionViewMode.COVER_FLOW) {
                                    Res.string.collection_cover_flow
                                } else {
                                    Res.string.collection_calendar
                                },
                            ),
                        color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionScreenHeaderComponentPreview() {
    CollectionPreviewTheme {
        CollectionScreenHeader(
            nickname = "오늘도히유",
            cardCount = 5,
        )
    }
}

@Preview(name = "컬렉션 · 커버플로", showBackground = true)
@Composable
private fun CollectionScreenPreview() {
    CollectionPreview()
}

@Preview(name = "컬렉션 · 작은 화면", showBackground = true)
@Composable
private fun CollectionCompactPreview() {
    CollectionPreview()
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

@Preview(name = "컬렉션 · 조회 실패", widthDp = 390, heightDp = 640, showBackground = true)
@Composable
private fun CollectionErrorPreview() {
    CollectionPreviewTheme {
        CollectionScreen(
            uiModel = CollectionScreenUiModel(nickname = "오늘도히유", cards = emptyList(), cardsError = "조회 실패"),
            onSaveCard = { _, _ -> },
            onShareCard = { _, _ -> },
        )
    }
}

@Preview(name = "컬렉션 · 목록 갱신 실패", widthDp = 390, heightDp = 640, showBackground = true)
@Composable
private fun CollectionRefreshErrorPreview() {
    CollectionPreviewTheme {
        CollectionScreen(
            uiModel =
                CollectionScreenUiModel(
                    nickname = "오늘도히유",
                    cards = collectionPreviewCards(),
                    cardsError = "조회 실패",
                ),
            onSaveCard = { _, _ -> },
            onShareCard = { _, _ -> },
        )
    }
}

@Composable
private fun CollectionPreview() {
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
        )
    }
}

internal fun collectionPreviewPendingProgress() =
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
internal fun collectionPreviewCards(): List<CollectionCardUiModel> {
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
