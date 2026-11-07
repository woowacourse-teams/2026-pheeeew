package com.pheeeew.feature.screens.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.feature.monitoring.product.ProductEvents
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
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
    onOpenCalendarDay: (month: String, cardId: String) -> Unit,
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
    val currentRefreshActionChanged by rememberUpdatedState(onRefreshActionChanged)
    DisposableEffect(viewModel) {
        currentRefreshActionChanged(viewModel::refresh)
        onDispose { currentRefreshActionChanged(null) }
    }
    LaunchedEffect(viewModel, sessionGeneration, currentNickname, refreshRevision) {
        viewModel.onSessionChanged(sessionGeneration, currentNickname)
        if (refreshRevision > 0L) viewModel.refreshFromNotification()
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        val dateObserver = scope.launch { viewModel.observeCalendarDate() }
        onPauseOrDispose { dateObserver.cancel() }
    }

    CollectionScreen(
        uiModel =
            viewModel.screenUiModel(
                state = state,
                notificationDate = notificationDate,
                imageActionResult = exportState.result,
                imageActionResultId = exportState.resultId,
                imageActionPhase = exportState.phase,
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
        onLoadMore = viewModel::loadNextPage,
        onRefresh = viewModel::refresh,
        onRetryToday = viewModel::refreshToday,
        onDismissCard = viewModel::dismissCard,
        onCardClick = viewModel::selectCard,
        onRetryCard = viewModel::retrySelectedCard,
        onCalendarMonthChange = viewModel::setCalendarMonth,
        onCalendarDayClick = { onOpenCalendarDay(state.calendarMonth, it) },
    )
}
