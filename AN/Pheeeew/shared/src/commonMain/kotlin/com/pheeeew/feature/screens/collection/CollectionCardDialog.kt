package com.pheeeew.feature.screens.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.RaisedCardButton
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.card.CollectionCard
import com.pheeeew.feature.screens.collection.card.CollectionCardImageLoadState
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_close
import pheeeew.shared.generated.resources.collection_image_creating
import pheeeew.shared.generated.resources.collection_image_saving
import pheeeew.shared.generated.resources.collection_image_sharing
import pheeeew.shared.generated.resources.collection_loading
import pheeeew.shared.generated.resources.collection_retry
import pheeeew.shared.generated.resources.collection_save
import pheeeew.shared.generated.resources.collection_saved
import pheeeew.shared.generated.resources.collection_share
import pheeeew.shared.generated.resources.ic_check_circle
import pheeeew.shared.generated.resources.ic_close
import pheeeew.shared.generated.resources.ic_collection_save
import pheeeew.shared.generated.resources.ic_collection_share
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val LOADING_INDICATOR_DELAY_MILLIS = 150L
private const val LOADING_INDICATOR_MINIMUM_MILLIS = 300L
private const val SAVE_COMPLETION_DISPLAY_MILLIS = 2_000L

@Composable
internal fun CollectionCardDialog(
    card: CollectionCardUiModel,
    isLoading: Boolean = false,
    error: String? = null,
    isSaveCompleted: Boolean = false,
    saveCompletionId: Int = 0,
    imageActionPhase: CollectionCardExportPhase = CollectionCardExportPhase.IDLE,
    onDismissSaveCompletion: () -> Unit = {},
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    onSave: (capture: suspend () -> ByteArray) -> Unit,
    onShare: (capture: suspend () -> ByteArray) -> Unit,
    onCancelCapture: (String) -> Unit = {},
) {
    AppDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CollectionCardDialogContent(
            card = card,
            isLoading = isLoading,
            error = error,
            isSaveCompleted = isSaveCompleted,
            saveCompletionId = saveCompletionId,
            imageActionPhase = imageActionPhase,
            onDismissSaveCompletion = onDismissSaveCompletion,
            onDismiss = onDismiss,
            onRetry = onRetry,
            onSave = onSave,
            onShare = onShare,
            onCancelCapture = onCancelCapture,
        )
    }
}

@Composable
private fun CollectionCardDialogContent(
    card: CollectionCardUiModel,
    isLoading: Boolean = false,
    error: String? = null,
    isSaveCompleted: Boolean = false,
    saveCompletionId: Int = 0,
    imageActionPhase: CollectionCardExportPhase = CollectionCardExportPhase.IDLE,
    onDismissSaveCompletion: () -> Unit = {},
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    onSave: (capture: suspend () -> ByteArray) -> Unit,
    onShare: (capture: suspend () -> ByteArray) -> Unit,
    onCancelCapture: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val graphicsContext = LocalGraphicsContext.current
    val captureLayer =
        remember(graphicsContext, card.id, card.characterImageUrl, card.textStampImageUrl, card.roundStampImageUrl) {
            graphicsContext.createGraphicsLayer()
        }
    val cancelCapture by rememberUpdatedState(onCancelCapture)
    DisposableEffect(graphicsContext, captureLayer) {
        val capturedCardId = card.id
        onDispose {
            cancelCapture(capturedCardId)
            graphicsContext.releaseGraphicsLayer(captureLayer)
        }
    }
    var imageLoadState by remember(card.id, card.characterImageUrl, card.textStampImageUrl, card.roundStampImageUrl) {
        mutableStateOf(CollectionCardImageLoadState.LOADING)
    }
    val isBusy = imageActionPhase != CollectionCardExportPhase.IDLE
    val isImageReady = imageLoadState == CollectionCardImageLoadState.READY
    val canExport = isImageReady && !isBusy
    val showImageLoading =
        rememberDelayedLoading(
            isLoading = imageLoadState == CollectionCardImageLoadState.LOADING || isBusy,
            key = card.id,
        )
    val showDetailLoading = rememberDelayedLoading(isLoading = isLoading, key = card.id)
    val dismissSaveCompletion by rememberUpdatedState(onDismissSaveCompletion)

    LaunchedEffect(card.id, isSaveCompleted, saveCompletionId) {
        if (isSaveCompleted) {
            delay(SAVE_COMPLETION_DISPLAY_MILLIS)
            dismissSaveCompletion()
        }
    }

    fun export(action: (suspend () -> ByteArray) -> Unit) {
        if (!canExport) return
        action {
            val bitmap = captureLayer.toImageBitmap()
            withContext(Dispatchers.Default) { bitmap.toPngBytes() }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(0.88f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, AppColors.GroupInk),
            color = AppColors.Surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.period,
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.collection_close),
                            tint = AppColors.GroupInk,
                        )
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Box(Modifier.width(minOf(maxWidth * 0.76f, maxHeight * 0.75f)).align(Alignment.Center)) {
                        CollectionCard(
                            uiModel = card,
                            onImageLoadState = { imageLoadState = it },
                            modifier =
                                Modifier
                                    .drawWithContent {
                                        captureLayer.record {
                                            this@drawWithContent.drawContent()
                                        }
                                        drawLayer(captureLayer)
                                    },
                        )
                        if (showImageLoading) {
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.Black.copy(alpha = 0.58f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularLoadingIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = AppColors.Primary,
                                        strokeWidth = 3.dp,
                                    )
                                    Text(
                                        text =
                                            stringResource(
                                                when (imageActionPhase) {
                                                    CollectionCardExportPhase.SAVING,
                                                    -> Res.string.collection_image_saving

                                                    CollectionCardExportPhase.SHARING,
                                                    -> Res.string.collection_image_sharing

                                                    else -> Res.string.collection_image_creating
                                                },
                                            ),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                when {
                    showDetailLoading -> {
                        Text(
                            stringResource(Res.string.collection_loading),
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                        )
                    }

                    error != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                error,
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry) { Text(stringResource(Res.string.collection_retry)) }
                        }
                    }
                }
                CollectionActionButton(
                    title =
                        stringResource(
                            if (isSaveCompleted) Res.string.collection_saved else Res.string.collection_save,
                        ),
                    icon = if (isSaveCompleted) Res.drawable.ic_check_circle else Res.drawable.ic_collection_save,
                    background = AppColors.Primary,
                    onClick = { export(onSave) },
                    enabled = isSaveCompleted || canExport,
                    dimmed = !isSaveCompleted && !isImageReady,
                )
                CollectionActionButton(
                    title = stringResource(Res.string.collection_share),
                    icon = Res.drawable.ic_collection_share,
                    background = AppColors.Surface,
                    onClick = { export(onShare) },
                    enabled = canExport,
                    dimmed = !isImageReady,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberDelayedLoading(
    isLoading: Boolean,
    key: Any?,
): Boolean {
    var isVisible by remember(key) { mutableStateOf(false) }
    var visibleSince by remember(key) { mutableStateOf<TimeMark?>(null) }

    LaunchedEffect(isLoading, key) {
        if (isLoading) {
            if (!isVisible) delay(LOADING_INDICATOR_DELAY_MILLIS)
            if (!isVisible) {
                visibleSince = TimeSource.Monotonic.markNow()
                isVisible = true
            }
        } else if (isVisible) {
            val elapsedMillis =
                visibleSince?.elapsedNow()?.inWholeMilliseconds ?: LOADING_INDICATOR_MINIMUM_MILLIS
            val remainingMillis = (LOADING_INDICATOR_MINIMUM_MILLIS - elapsedMillis).coerceAtLeast(0L)
            delay(remainingMillis)
            isVisible = false
            visibleSince = null
        }
    }

    return isVisible
}

@Composable
private fun CollectionActionButton(
    title: String,
    icon: DrawableResource,
    background: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    RaisedCardButton(
        backgroundColor = background,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().alpha(if (dimmed) 0.5f else 1f),
        enabled = enabled,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AppColors.GroupInk,
                modifier = Modifier.size(22.dp),
            )
            Text(text = title, fontWeight = FontWeight.Bold, color = AppColors.GroupInk)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CollectionCardDialogContentPreview() {
    CollectionPreviewTheme {
        CollectionCardDialogContent(
            card =
                CollectionCardUiModel(
                    id = "preview-exhausted",
                    period = "2026.10.18",
                    title = "만성 퇴근 희망자",
                    emotion = EmotionType.EXHAUSTED,
                    pressLabel = "지침 프레스",
                    pressCount = 312,
                    stampLabel = "지침 스탬프",
                    stampCount = 23,
                    characterImageUrl = Res.getUri("drawable/tmp_emotionimg.png"),
                    textStampImageUrl = Res.getUri("drawable/tmp_text_stamp.png"),
                    roundStampImageUrl = Res.getUri("drawable/tmp_round_stamp.png"),
                ),
            onDismiss = {},
            onSave = {},
            onShare = {},
        )
    }
}
