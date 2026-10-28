package com.pheeeew.feature.screens.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.domain.model.collection.COLLECTION_PRESS_CLAIM_TARGET
import com.pheeeew.domain.model.collection.COLLECTION_STAMP_CLAIM_TARGET
import com.pheeeew.domain.model.collection.CollectionCardReference
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.monitoring.product.ProductEvents
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.uuid.Uuid

@Composable
internal fun CollectionRoute(
    monitoring: Monitoring,
    sessionGeneration: Long?,
    currentNickname: String?,
    notificationDate: String? = null,
    refreshRevision: Long = 0L,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    onRefreshActionChanged: ((() -> Unit)?) -> Unit = {},
) {
    val viewModel: CollectionViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry = remember(monitoring) { ProductMonitoring(monitoring, "collection") }
    val cardId = state.selectedCard?.id
    val cardViewId =
        remember(cardId) {
            Uuid
                .random()
                .toString()
        }
    val cardFields =
        mapOf(
            "view_id" to
                EventValue
                    .Text(cardViewId),
        )
    LaunchedEffect(cardId, cardViewId) {
        if (cardId != null) telemetry.emit(ProductEvents.COLLECTION_CARD_VIEWED, cardFields)
    }
    val scope = rememberCoroutineScope()
    val imageActions = rememberCollectionCardImageActions()
    val exportState =
        remember(sessionGeneration, scope, imageActions) {
            CollectionCardExportState(scope, imageActions)
        }
    DisposableEffect(exportState) {
        onDispose { exportState.dispose() }
    }
    val imageActionResult = exportState.result
    val currentRefreshActionChanged by rememberUpdatedState(onRefreshActionChanged)
    DisposableEffect(viewModel) {
        currentRefreshActionChanged(viewModel::refresh)
        onDispose { currentRefreshActionChanged(null) }
    }
    LaunchedEffect(viewModel, sessionGeneration, currentNickname, refreshRevision) {
        viewModel.onSessionChanged(sessionGeneration, currentNickname)
        if (refreshRevision > 0L) viewModel.refreshFromNotification()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    CollectionScreen(
        uiModel =
            state.toScreenUiModel(
                saveCompletionCardId =
                    imageActionResult
                        ?.takeIf {
                            it.action == CollectionImageAction.SAVE &&
                                it.outcome == CollectionImageActionOutcome.SUCCEEDED
                        }?.cardId,
                saveCompletionId = exportState.resultId,
                imageActionResult = imageActionResult,
                imageActionErrorId = exportState.resultId,
                imageActionPhase = exportState.phase,
                notificationDate = notificationDate,
            ),
        onSaveCard = { cardId, capture ->
            telemetry.emit(ProductEvents.COLLECTION_CARD_SAVE_CLICKED, cardFields)
            exportState.export(cardId, CollectionImageAction.SAVE, capture)
        },
        onShareCard = { cardId, capture ->
            telemetry.emit(ProductEvents.COLLECTION_CARD_SHARE_CLICKED, cardFields)
            exportState.export(cardId, CollectionImageAction.SHARE, capture)
        },
        onCancelCardCapture = exportState::cancelCapture,
        onDismissSaveCompletion = exportState::dismissResult,
        modifier = modifier,
        bottomBar = bottomBar,
        onClaim = { emotion ->
            telemetry.emit(ProductEvents.COLLECTION_CLAIM_CLICKED)
            viewModel.claim(emotion)
        },
        onRetryClaim = viewModel::retryClaim,
        onFilterChange = viewModel::setFilter,
        onLoadMore = viewModel::loadNextPage,
        onRefresh = viewModel::refresh,
        onRetryCards = viewModel::retryCards,
        onRetryToday = viewModel::refreshToday,
        onDismissCard = viewModel::dismissCard,
        onCardClick = viewModel::selectCard,
        onRetryCard = viewModel::retrySelectedCard,
    )
}

private fun CollectionUiState.toScreenUiModel(
    saveCompletionCardId: String?,
    saveCompletionId: Int,
    imageActionResult: CollectionImageActionResult?,
    imageActionErrorId: Int,
    imageActionPhase: CollectionCardExportPhase,
    notificationDate: String?,
): CollectionScreenUiModel =
    CollectionScreenUiModel(
        isNotificationDateOutdated = notificationDate != null && today != null && notificationDate != today.date,
        nickname = nickname?.takeIf(String::isNotBlank) ?: "나",
        cards = cards,
        totalCount = totalCount,
        emotionCounts = emotionCounts,
        selectedFilter = selectedFilter,
        pendingProgress =
            today
                ?.takeUnless { it.hasClaimedCard }
                ?.let { progress ->
                    CollectionProgressUiModel(
                        emotion = progress.claimEmotion?.toUi() ?: collectionCoverFlowEmotionOrder.first(),
                        stampCount = progress.totalStampCount,
                        stampTarget = COLLECTION_STAMP_CLAIM_TARGET,
                        stampRemaining = (COLLECTION_STAMP_CLAIM_TARGET - progress.totalStampCount).coerceAtLeast(0),
                        stampProgressByEmotion =
                            collectionCoverFlowEmotionOrder.mapNotNull { emotion ->
                                progress.items
                                    .firstOrNull { it.state.name == emotion.name }
                                    ?.let { item -> CollectionEmotionProgress(emotion, item.stamps.count) }
                            },
                        pressCount = progress.totalPressCount,
                        pressTarget = COLLECTION_PRESS_CLAIM_TARGET,
                        pressRemaining = (COLLECTION_PRESS_CLAIM_TARGET - progress.totalPressCount).coerceAtLeast(0),
                        canClaim = progress.canClaim,
                        pressProgressByEmotion =
                            collectionCoverFlowEmotionOrder.mapNotNull { emotion ->
                                progress.items
                                    .firstOrNull { it.state.name == emotion.name }
                                    ?.let { item -> CollectionEmotionProgress(emotion, item.presses.count) }
                            },
                        hasProgress = true,
                        isClaiming = claimingState != null,
                    )
                },
        todayLoading = isLoadingToday,
        todayError = todayError,
        isLoadingCards = isLoadingCards,
        isRefreshRequestActive = isRefreshRequestActive,
        isRefreshIndicatorVisible = isRefreshIndicatorVisible,
        scrollToTopRequestId = scrollToTopRequestId,
        cardsError = cardsError,
        isLoadingNext = isLoadingNext,
        hasNext = hasNext,
        claimError = claimError,
        claimOutcomeUnknown = claimOutcomeUnknown,
        selectedCard = selectedCard,
        isLoadingCard = isLoadingCard,
        cardError = cardError,
        saveCompletionCardId = saveCompletionCardId,
        saveCompletionId = saveCompletionId,
        imageActionError =
            when {
                imageActionResult?.outcome == CollectionImageActionOutcome.CAPTURE_FAILED -> {
                    CollectionCardActionError.CAPTURE
                }

                imageActionResult?.outcome == CollectionImageActionOutcome.FAILED &&
                    imageActionResult.action == CollectionImageAction.SAVE -> {
                    CollectionCardActionError.SAVE
                }

                imageActionResult?.outcome == CollectionImageActionOutcome.FAILED -> {
                    CollectionCardActionError.SHARE
                }

                else -> {
                    null
                }
            },
        imageActionErrorCardId = imageActionResult?.cardId,
        imageActionErrorId = imageActionErrorId,
        imageActionPhase = imageActionPhase,
    )

private fun com.pheeeew.domain.model.emotion.EmotionState.toUi(): EmotionType = EmotionType.valueOf(name)

private fun EmotionType.labelFallback(): String =
    when (this) {
        EmotionType.FRUSTRATED -> "답답"
        EmotionType.IRRITATED -> "짜증"
        EmotionType.EXHAUSTED -> "지침"
        EmotionType.DISCOURAGED -> "좌절"
        EmotionType.ANGRY -> "분노"
    }

private fun CollectionCardReference.toUiModel(
    nickname: String?,
    stampCount: Long,
    pressCount: Long,
): CollectionCardUiModel {
    val emotion = state.toUi()
    return CollectionCardUiModel(
        id = cardId,
        period = formatCollectionCardDate(date),
        title = nickname?.takeIf(String::isNotBlank) ?: "나",
        emotion = emotion,
        pressLabel = "${emotion.labelFallback()} 프레스",
        pressCount = pressCount,
        stampLabel = "${emotion.labelFallback()} 스탬프",
        stampCount = stampCount,
        characterImageUrl = images.characterUrl,
        textStampImageUrl = images.straightStampUrl,
        roundStampImageUrl = images.roundStampUrl,
    )
}
