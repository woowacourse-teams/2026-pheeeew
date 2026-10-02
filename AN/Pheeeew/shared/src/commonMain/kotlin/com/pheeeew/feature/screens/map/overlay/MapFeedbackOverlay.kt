package com.pheeeew.feature.screens.map.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.pheeeew.core.designsystem.component.AppPopup
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.feature.screens.map.MapFeedbackAction
import com.pheeeew.feature.screens.map.MapUiModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailLoadUiModel
import com.pheeeew.feature.screens.map.primaryFeedback
import com.pheeeew.feature.screens.map.record.RecordNoticeUiModel

internal enum class MapFeedbackSource {
    Connection,
    Record,
    Message,
    Detail,
    Map,
}

/** Select one notification, including errors caused by the same lost connection. */
internal fun selectMapFeedback(
    uiModel: MapUiModel,
    recording: Boolean,
    connectionMessage: String?,
    notice: RecordNoticeUiModel?,
    message: String?,
    detailError: EmotionDetailLoadUiModel.Failed?,
): MapFeedbackSource? =
    when {
        connectionMessage != null -> MapFeedbackSource.Connection
        notice != null && (!uiModel.isOffline || !notice.suppressWhenOffline) -> MapFeedbackSource.Record
        message != null -> MapFeedbackSource.Message
        uiModel.isOffline -> if (recording) null else MapFeedbackSource.Map
        detailError != null -> MapFeedbackSource.Detail
        uiModel.primaryFeedback() != null -> MapFeedbackSource.Map
        else -> null
    }

/** Mounted only in the foremost map/record surface, above its modal scrim. */
@Composable
internal fun MapFeedbackOverlay(
    uiModel: MapUiModel,
    notice: RecordNoticeUiModel?,
    onDismissNotice: () -> Unit,
    message: String?,
    onMessageDismiss: () -> Unit,
    detailError: EmotionDetailLoadUiModel.Failed?,
    onRetryDetail: () -> Unit,
    onDismissDetailError: () -> Unit,
    onAction: (MapFeedbackAction) -> Unit,
    recording: Boolean,
    connectionMessage: String?,
    top: Int,
) {
    val source = selectMapFeedback(uiModel, recording, connectionMessage, notice, message, detailError)
    val mapFeedback = uiModel.primaryFeedback()
    val text =
        when (source) {
            MapFeedbackSource.Connection -> connectionMessage
            MapFeedbackSource.Record -> notice?.message
            MapFeedbackSource.Message -> message
            MapFeedbackSource.Detail -> detailError?.message
            MapFeedbackSource.Map -> mapFeedback?.message
            null -> null
        }
    val dismiss =
        when (source) {
            MapFeedbackSource.Record -> onDismissNotice
            MapFeedbackSource.Message -> onMessageDismiss
            MapFeedbackSource.Detail -> onDismissDetailError
            else -> ({})
        }
    val action: (() -> Unit)? =
        when (source) {
            MapFeedbackSource.Detail -> onRetryDetail.takeIf { detailError?.canRetry == true }
            MapFeedbackSource.Map -> mapFeedback?.action?.let { action -> { onAction(action) } }
            else -> null
        }
    if (text != null) {
        AppPopup(
            popupPositionProvider = MapFeedbackPosition(top),
            properties = mapFeedbackPopupProperties(),
        ) {
            Snackbar(
                message = text,
                isError =
                    when (source) {
                        MapFeedbackSource.Connection -> uiModel.isOffline
                        MapFeedbackSource.Record -> notice?.isError == true
                        MapFeedbackSource.Detail, MapFeedbackSource.Map -> true
                        else -> false
                    },
                onDismiss = dismiss,
                maxLines = 3,
                presentationKey = if (source == MapFeedbackSource.Record) notice else source to text,
                onShown = { if (source == MapFeedbackSource.Record) notice?.receipt?.shown() },
                durationMillis =
                    if (source == MapFeedbackSource.Record || source == MapFeedbackSource.Message) 3_000L else null,
                actionLabel =
                    when {
                        action == null -> null
                        source == MapFeedbackSource.Detail -> "다시 시도"
                        else -> mapFeedback?.action?.label
                    },
                onAction = action,
                showDismissAction = source == MapFeedbackSource.Detail,
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

private data class MapFeedbackPosition(
    val top: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset((windowSize.width - popupContentSize.width) / 2, top)
}
