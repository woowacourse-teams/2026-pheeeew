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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.navigation.CollectionDayDestination
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.monitoring.product.ProductEvents
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.screens.collection.CollectionCardActionError
import com.pheeeew.feature.screens.collection.CollectionCardDialog
import com.pheeeew.feature.screens.collection.CollectionCardExportState
import com.pheeeew.feature.screens.collection.CollectionDayDetailUiState
import com.pheeeew.feature.screens.collection.CollectionDayEmotionContent
import com.pheeeew.feature.screens.collection.CollectionDayRetryTarget
import com.pheeeew.feature.screens.collection.CollectionImageAction
import com.pheeeew.feature.screens.collection.CollectionImageActionOutcome
import com.pheeeew.feature.screens.collection.CollectionViewModel
import com.pheeeew.feature.screens.collection.calendar.CollectionRetryState
import com.pheeeew.feature.screens.collection.rememberCollectionCardImageActions
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.app_cancel
import pheeeew.shared.generated.resources.collection_day_cards_error
import pheeeew.shared.generated.resources.emotion_delete_action
import pheeeew.shared.generated.resources.emotion_delete_confirm
import pheeeew.shared.generated.resources.emotion_delete_notice
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_frustrated
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
    val detailState =
        remember(viewModel, destination, restoredCardId) {
            viewModel.calendarDetailUiState(destination.month, restoredCardId ?: destination.cardId)
        }
    val state by detailState.collectAsStateWithLifecycle(initialValue = CollectionDayDetailUiState())
    var expandedCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val telemetry = remember(monitoring) { ProductMonitoring(monitoring, "collection") }
    if (state.deleteTargetId != null) {
        val notice = stringResource(Res.string.emotion_delete_notice)
        ConfirmDialog(
            title = stringResource(Res.string.emotion_delete_confirm),
            content = if (state.deleteFailed) "$notice\n\n삭제하지 못했어요. 다시 시도해 주세요." else notice,
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

    LifecycleResumeEffect(viewModel) {
        viewModel.onCalendarDetailResumed()
        val dateObserver = scope.launch { viewModel.observeCalendarDate() }
        onPauseOrDispose { dateObserver.cancel() }
    }

    val cards = state.cards
    val selectedDate = state.selectedDate
    val cardError = state.cardLoadError
    val isCommonError = cardError?.retryTarget == CollectionDayRetryTarget.All
    val retryCardLoad: () -> Unit = {
        cardError?.let { viewModel.retryCalendarDetailCards(it.retryTarget) }
    }
    LaunchedEffect(selectedDate) {
        selectedDate?.let(onSelectedCardChanged)
    }
    LaunchedEffect(viewModel, expandedCardId, cards.any { it.id == expandedCardId }) {
        val cardId = expandedCardId ?: return@LaunchedEffect
        if (cards.any { it.id == cardId }) viewModel.selectCalendarCardDetail(cardId)
    }

    Box(Modifier.fillMaxSize().background(AppColors.GroupBackground).statusBarsPadding()) {
        if (selectedDate == null) {
            if (state.isLoadingCalendar) {
                CircularLoadingIndicator(Modifier.align(Alignment.Center), color = AppColors.Primary)
            } else {
                if (cardError != null) {
                    Column(Modifier.fillMaxSize()) {
                        CollectionDayHeader(title = state.errorHeader, onBack = onBack)
                        EmotionListLoadError(
                            title = stringResource(Res.string.collection_day_cards_error),
                            message = cardError.message,
                            hasItems = false,
                            icon =
                                if (isCommonError) {
                                    Res.drawable.ic_emotion_frustrated
                                } else {
                                    Res.drawable.ic_emotion_discouraged
                                },
                            onRetry = retryCardLoad,
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
            val emotions = (state.emotionContent as? CollectionDayEmotionContent.Items)?.items.orEmpty()
            val audio =
                rememberCollectionDayAudio(
                    sessionGeneration,
                    selectedDate,
                    emotions,
                    requireNotNull(viewModel.audioLoader),
                )
            CollectionDayDetailScreen(
                pages = state.pages,
                selectedCardId = selectedDate,
                todayDate = state.todayDate,
                todayProgress = state.todayProgress,
                isLoadingToday = state.isLoadingToday,
                isLoadingCalendar = state.isLoadingCalendar,
                todayClaimError = state.claimError,
                cardLoadError = cardError?.message,
                isCommonError = isCommonError,
                onRetryCardLoad = retryCardLoad,
                onClaimToday = { emotion ->
                    telemetry.emit(ProductEvents.COLLECTION_CLAIM_CLICKED)
                    viewModel.claim(emotion)
                },
                onRetryTodayClaim = viewModel::retryClaim,
                nickname = state.nickname,
                emotionContent = state.emotionContent,
                emotionCount = state.emotionCount,
                audio = audio,
                onBack = onBack,
                onCardSettled = viewModel::selectCalendarDate,
                onOpenCard = { expandedCardId = it },
                onLoadMoreEmotions = viewModel::loadNextCalendarEmotions,
                onRetryEmotions = viewModel::retryCalendarEmotions,
                onRetryNextEmotions = viewModel::retryNextCalendarEmotions,
                onDeleteEmotion = viewModel::requestEmotionDeletion,
                onReactToEmotion = viewModel::reactToCalendarEmotion,
                pendingReactionIds = state.pendingReactionIds,
                reactionError = state.reactionError,
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
