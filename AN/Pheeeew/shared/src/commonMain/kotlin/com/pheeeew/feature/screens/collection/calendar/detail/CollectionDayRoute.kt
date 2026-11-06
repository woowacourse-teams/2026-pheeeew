package com.pheeeew.feature.screens.collection.calendar.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.navigation.CollectionDayDestination
import com.pheeeew.domain.model.collection.CollectionEmotion
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.monitoring.product.ProductEvents
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.screens.collection.CollectionCardActionError
import com.pheeeew.feature.screens.collection.CollectionCardDialog
import com.pheeeew.feature.screens.collection.CollectionCardExportState
import com.pheeeew.feature.screens.collection.CollectionDayEmotionUiModel
import com.pheeeew.feature.screens.collection.CollectionDayEmojiUiModel
import com.pheeeew.feature.screens.collection.CollectionImageAction
import com.pheeeew.feature.screens.collection.CollectionImageActionOutcome
import com.pheeeew.feature.screens.collection.CollectionViewModel
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.screens.collection.calendar.CollectionRetryState
import com.pheeeew.feature.screens.collection.calendar.collectionMonthDays
import com.pheeeew.feature.screens.collection.calendar.currentCollectionTodayDate
import com.pheeeew.feature.screens.collection.rememberCollectionCardImageActions
import com.pheeeew.feature.screens.collection.todayProgress
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_day_cards_error
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.app_cancel
import pheeeew.shared.generated.resources.emotion_delete_action
import pheeeew.shared.generated.resources.emotion_delete_confirm
import pheeeew.shared.generated.resources.emotion_delete_notice
import kotlin.uuid.Uuid

@Composable
internal fun CollectionDayRoute(
    destination: CollectionDayDestination,
    viewModel: CollectionViewModel,
    sessionGeneration: Long?,
    currentNickname: String?,
    monitoring: Monitoring,
    restoredCardId: String?,
    onSelectedCardChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val telemetry = remember(monitoring) { ProductMonitoring(monitoring, "collection") }
    val day = state.day
    if (day.emotionDeleteTargetId != null) {
        val notice = stringResource(Res.string.emotion_delete_notice)
        ConfirmDialog(
            title = stringResource(Res.string.emotion_delete_confirm),
            content = if (day.emotionDeleteFailed) "$notice\n\n삭제하지 못했어요. 다시 시도해 주세요." else notice,
            confirmText = stringResource(Res.string.emotion_delete_action),
            cancelText = stringResource(Res.string.app_cancel),
            onConfirm = viewModel::confirmEmotionDeletion,
            onCancel = viewModel::cancelEmotionDeletion,
        )
    }
    val imageActions = rememberCollectionCardImageActions()
    val export =
        remember(sessionGeneration, scope, imageActions) {
            CollectionCardExportState(
                scope,
                imageActions,
            )
        }
    DisposableEffect(export) { onDispose { export.dispose() } }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.closeCalendarDay()
            viewModel.dismissCard()
        }
    }
    LaunchedEffect(viewModel, sessionGeneration, currentNickname) {
        viewModel.onSessionChanged(sessionGeneration, currentNickname)
    }
    LaunchedEffect(viewModel, sessionGeneration, destination) {
        viewModel.onSessionChanged(sessionGeneration, currentNickname)
        viewModel.openCalendarDay(destination.month, restoredCardId ?: destination.cardId)
    }

    val cards = state.calendarCards.takeIf { state.calendarMonth == destination.month }.orEmpty()
    val selectedDate = day.selectedCalendarDate
    val todayDate = currentCollectionTodayDate()
    val todayProgress = state.todayProgress.takeIf { state.today?.date == todayDate }
    val dates = collectionMonthDays(destination.month).filter { it.isCurrentMonth }.mapNotNull { it.date }
        .filter { it <= todayDate }.asReversed()
    LaunchedEffect(selectedDate) {
        selectedDate?.let(onSelectedCardChanged)
    }
    LaunchedEffect(viewModel, expandedCardId, cards.any { it.id == expandedCardId }) {
        val cardId = expandedCardId ?: return@LaunchedEffect
        if (cards.any { it.id == cardId }) viewModel.selectCard(cardId)
    }

    Box(Modifier.fillMaxSize().background(AppColors.GroupBackground).statusBarsPadding()) {
        if (selectedDate == null) {
            if (state.isLoadingCalendar || state.calendarMonth != destination.month) {
                CircularLoadingIndicator(Modifier.align(Alignment.Center), color = AppColors.Primary)
            } else {
                val error = state.calendarError
                if (error != null) {
                    val isCommonError = state.commonError != null
                    Column(Modifier.fillMaxSize()) {
                        val date = (restoredCardId ?: destination.cardId).takeIf { it in dates }
                        val title = date?.let { "${it.substring(5, 7)}월 ${it.substring(8, 10)}일" } ?: "컬렉션"
                        CollectionDayHeader(title = title, onBack = onBack)
                        EmotionListLoadError(
                            title = stringResource(Res.string.collection_day_cards_error),
                            message = error,
                            hasItems = false,
                            icon = if (isCommonError) Res.drawable.ic_emotion_frustrated else Res.drawable.ic_emotion_discouraged,
                            onRetry = if (state.commonError != null) viewModel::refresh else viewModel::retryCalendar,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                } else {
                    CollectionRetryState(
                        message = "이 날짜의 카드를 찾을 수 없어요.",
                        onRetry = onBack,
                        modifier = Modifier.fillMaxSize(),
                        actionLabel = "달력으로 돌아가기",
                    )
                }
            }
        } else {
            val emotions = remember(day.calendarEmotions) { day.calendarEmotions.map { it.toUiModel() } }
            val audio =
                rememberCollectionDayAudio(
                    sessionGeneration,
                    selectedDate,
                    emotions,
                    requireNotNull(viewModel.audioLoader),
                )
            CollectionDayDetailScreen(
                cards = cards,
                selectedCardId = selectedDate,
                dates = dates,
                todayDate = todayDate,
                todayProgress = todayProgress,
                isLoadingToday = state.isLoadingToday,
                todayClaimError = state.claimError,
                cardLoadError = state.calendarError ?: state.todayError.takeIf { selectedDate == todayDate },
                isCommonError = state.commonError != null,
                onRetryCardLoad = when {
                    state.commonError != null -> viewModel::refresh
                    state.calendarError != null -> viewModel::retryCalendar
                    else -> viewModel::refreshToday
                },
                onClaimToday = { emotion ->
                    telemetry.emit(ProductEvents.COLLECTION_CLAIM_CLICKED)
                    viewModel.claim(emotion)
                },
                onRetryTodayClaim = viewModel::retryClaim,
                nickname = state.nickname ?: "나",
                emotions = emotions,
                emotionCount =
                    day.calendarEmotions.size.takeIf {
                        day.calendarEmotionsLoaded && day.calendarEmotionsError == null &&
                            !day.hasNextCalendarEmotions
                    },
                isLoadingEmotions = day.isLoadingCalendarEmotions,
                emotionsLoaded = day.calendarEmotionsLoaded,
                isLoadingNextEmotions = day.isLoadingNextCalendarEmotions,
                emotionsError = day.calendarEmotionsError,
                hasNextEmotions = day.hasNextCalendarEmotions,
                audio = audio,
                onBack = onBack,
                onCardSettled = viewModel::selectCalendarDate,
                onOpenCard = { expandedCardId = it },
                onLoadMoreEmotions = viewModel::loadNextCalendarEmotions,
                onRetryEmotions = viewModel::retryCalendarEmotions,
                onRetryNextEmotions = viewModel::retryNextCalendarEmotions,
                onDeleteEmotion = viewModel::requestEmotionDeletion,
                onReactToEmotion = viewModel::reactToCalendarEmotion,
                pendingReactionIds = day.pendingReactionIds,
                reactionError = day.calendarReactionError,
            )
        }
    }

    val card = cards.firstOrNull { it.id == expandedCardId } ?: return
    val detailedCard = state.selectedCard?.takeIf { it.id == card.id }
    val fields = remember(card.id) { mapOf("view_id" to EventValue.Text(Uuid.random().toString())) }
    LaunchedEffect(detailedCard?.id) {
        if (detailedCard != null) telemetry.emit(ProductEvents.COLLECTION_CARD_VIEWED, fields)
    }
    val result = export.result?.takeIf { it.cardId == card.id }
    CollectionCardDialog(
        card = detailedCard ?: card,
        isLoading = state.isLoadingCard,
        error = state.cardError,
        isSaveCompleted =
            result?.action == CollectionImageAction.SAVE && result.outcome == CollectionImageActionOutcome.SUCCEEDED,
        saveCompletionId = export.resultId,
        imageActionError =
            when (result?.outcome) {
                CollectionImageActionOutcome.CAPTURE_FAILED -> {
                    CollectionCardActionError.CAPTURE
                }

                CollectionImageActionOutcome.FAILED -> {
                    if (result.action ==
                        CollectionImageAction.SAVE
                    ) {
                        CollectionCardActionError.SAVE
                    } else {
                        CollectionCardActionError.SHARE
                    }
                }

                else -> {
                    null
                }
            },
        imageActionErrorCardId = result?.cardId,
        imageActionErrorId = export.resultId,
        imageActionPhase = export.phase,
        onDismissSaveCompletion = export::dismissResult,
        onDismiss = {
            export.dismissResult()
            expandedCardId = null
            viewModel.dismissCard()
        },
        onRetry = viewModel::retrySelectedCard,
        onSave = { capture ->
            telemetry.emit(ProductEvents.COLLECTION_CARD_SAVE_CLICKED, fields)
            export.export(card.id, CollectionImageAction.SAVE, capture)
        },
        onShare = { capture ->
            telemetry.emit(ProductEvents.COLLECTION_CARD_SHARE_CLICKED, fields)
            export.export(card.id, CollectionImageAction.SHARE, capture)
        },
        onCancelCapture = export::cancelCapture,
    )
}

private fun CollectionEmotion.toUiModel(): CollectionDayEmotionUiModel =
    CollectionDayEmotionUiModel(
        id = id,
        createdAt = createdAt,
        emotion = EmotionType.valueOf(state.name),
        contentType = contentType,
        memo = memo,
        playbackUrl = audio?.playbackUrl,
        playbackExpiresAt = audio?.expiresAt,
        emojis = emojis.map { CollectionDayEmojiUiModel(it.type, it.count, it.selected) },
    )
