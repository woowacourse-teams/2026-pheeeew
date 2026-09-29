package com.pheeeew.feature.screens.map.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.Snackbar
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.map.MapFeedbackAction
import com.pheeeew.feature.screens.map.MapUiModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailLoadUiModel
import com.pheeeew.feature.screens.map.primaryFeedback
import com.pheeeew.feature.screens.map.record.RecordNoticeUiModel
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_error

/** Sits below the status bar and the map's 48dp toolbar, without covering the map with a scrim. */
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
    modifier: Modifier = Modifier,
    suppressConnectionFeedback: Boolean = false,
) {
    Column(
        modifier.statusBarsPadding().padding(top = 76.dp, start = 16.dp, end = 16.dp).widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Notices share this column with persistent errors, so they never occupy the same slot.
        Snackbar(
            message = notice?.message,
            onDismiss = onDismissNotice,
            isError = notice?.isError == true,
            maxLines = 3,
            presentationKey = notice,
            onShown = { notice?.receipt?.shown() },
        )
        Snackbar(message, onMessageDismiss, maxLines = 3)
        uiModel.primaryFeedback()?.takeUnless { suppressConnectionFeedback && uiModel.isOffline }?.let { feedback ->
            MapErrorBanner(
                message = feedback.message,
                actionLabel = feedback.action?.label,
                onAction = feedback.action?.let { action -> { onAction(action) } },
            )
        }
        detailError?.let { error ->
            MapErrorBanner(
                message = error.message,
                actionLabel = "다시 시도".takeIf { error.canRetry && !uiModel.isOffline },
                onAction = onRetryDetail.takeIf { error.canRetry && !uiModel.isOffline },
                onDismiss = onDismissDetailError,
            )
        }
    }
}

@Composable
internal fun MapErrorBanner(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Background,
        border = BorderStroke(AppBorders.Standard, AppColors.Error),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(Res.drawable.ic_error), null, Modifier.size(20.dp), tint = AppColors.Error)
            Text(message, Modifier.weight(1f), color = AppColors.TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
            if (onAction != null && actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = AppColors.TextPrimary, fontSize = 12.sp) }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("닫기", color = AppColors.TextPrimary, fontSize = 12.sp) }
            }
        }
    }
}

@Preview(name = "지도 · 연결 오류와 기록 안내", widthDp = 360, showBackground = true)
@Composable
private fun MapFeedbackPreview() {
    MapFeedbackOverlay(
        uiModel = MapUiModel(isOffline = true),
        notice = RecordNoticeUiModel("감정을 등록하지 못했어요. 다시 시도해 주세요", true),
        onDismissNotice = {},
        message = null,
        onMessageDismiss = {},
        detailError = null,
        onRetryDetail = {},
        onDismissDetailError = {},
        onAction = {},
    )
}
