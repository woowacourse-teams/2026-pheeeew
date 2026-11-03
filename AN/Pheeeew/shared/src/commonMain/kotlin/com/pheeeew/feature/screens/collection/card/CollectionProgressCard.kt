package com.pheeeew.feature.screens.collection.card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionEmotionProgress
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import com.pheeeew.feature.screens.collection.CollectionProgressUiModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.collection_activity_count
import pheeeew.shared.generated.resources.collection_claim_condition_met
import pheeeew.shared.generated.resources.collection_claim_condition_not_met
import pheeeew.shared.generated.resources.collection_goal_reached
import pheeeew.shared.generated.resources.collection_goal_remaining
import pheeeew.shared.generated.resources.collection_issue_card
import pheeeew.shared.generated.resources.collection_loading
import pheeeew.shared.generated.resources.collection_press_label
import pheeeew.shared.generated.resources.collection_progress_card_accessibility_description
import pheeeew.shared.generated.resources.collection_progress_unavailable
import pheeeew.shared.generated.resources.collection_retry
import pheeeew.shared.generated.resources.collection_stamp_label
import pheeeew.shared.generated.resources.collection_tomorrow_card_accessibility_description
import pheeeew.shared.generated.resources.collection_tomorrow_card_message
import pheeeew.shared.generated.resources.ic_highlighter_underline

@Composable
internal fun CollectionProgressCard(
    progress: CollectionProgressUiModel,
    isLoading: Boolean,
    error: String?,
    onClaim: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardDescription =
        stringResource(
            Res.string.collection_progress_card_accessibility_description,
            stringResource(progress.emotion.label),
        )
    CollectionCardFrame(
        cornerIconTint = progress.emotion.representativeColor,
        modifier =
            modifier.semantics {
                contentDescription = cardDescription
            },
    ) { scale ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp * scale, vertical = 22.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            when {
                progress.hasProgress && error == null -> {
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        var conditionTextHeightPx by remember(progress.canClaim) { mutableIntStateOf(0) }
                        val density = LocalDensity.current
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 34.dp * scale),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.ic_highlighter_underline),
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(0.70f)
                                        .height(with(density) { (conditionTextHeightPx * 0.8f).toDp() }),
                            )
                            Text(
                                text =
                                    stringResource(
                                        if (progress.canClaim) {
                                            Res.string.collection_claim_condition_met
                                        } else {
                                            Res.string.collection_claim_condition_not_met
                                        },
                                    ),
                                color = AppColors.TextPrimary,
                                fontSize = (16 * scale).sp,
                                lineHeight = (20 * scale).sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                onTextLayout = { conditionTextHeightPx = it.size.height },
                            )
                        }

                        Spacer(modifier = Modifier.height(36.dp * scale))
                        CollectionActivityProgressRow(
                            label = stringResource(Res.string.collection_stamp_label),
                            count = progress.stampCount,
                            target = progress.stampTarget,
                            remaining = progress.stampRemaining,
                            color = progress.emotion.representativeColor,
                            emotionProgress = progress.stampProgressByEmotion,
                            scale = scale,
                        )
                        Spacer(modifier = Modifier.height(20.dp * scale))
                        CollectionActivityProgressRow(
                            label = stringResource(Res.string.collection_press_label),
                            count = progress.pressCount,
                            target = progress.pressTarget,
                            remaining = progress.pressRemaining,
                            color = progress.emotion.representativeColor,
                            emotionProgress = progress.pressProgressByEmotion,
                            scale = scale,
                        )
                    }
                }

                error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            color = AppColors.TextSecondary,
                            fontSize = (11 * scale).sp,
                            lineHeight = (16 * scale).sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(Res.string.collection_retry), fontSize = (11 * scale).sp)
                        }
                    }
                }

                isLoading -> {
                    Text(
                        text = stringResource(Res.string.collection_loading),
                        color = AppColors.TextSecondary,
                        fontSize = (12 * scale).sp,
                    )
                }

                progress.hasProgress -> {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp * scale),
                    ) {
                        CollectionActivityProgressRow(
                            label = stringResource(Res.string.collection_stamp_label),
                            count = progress.stampCount,
                            target = progress.stampTarget,
                            remaining = progress.stampRemaining,
                            color = progress.emotion.representativeColor,
                            scale = scale,
                        )
                        CollectionActivityProgressRow(
                            label = stringResource(Res.string.collection_press_label),
                            count = progress.pressCount,
                            target = progress.pressTarget,
                            remaining = progress.pressRemaining,
                            color = progress.emotion.representativeColor,
                            emotionProgress = progress.pressProgressByEmotion,
                            scale = scale,
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(Res.string.collection_progress_unavailable),
                        color = AppColors.TextSecondary,
                        fontSize = (11 * scale).sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Button(
                onClick = onClaim,
                enabled =
                    progress.hasProgress &&
                        progress.canClaim &&
                        !progress.isClaiming &&
                        !isLoading &&
                        error == null,
                shape = RoundedCornerShape(12.dp * scale),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.GroupInk,
                    ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp * scale),
            ) {
                Text(
                    text = stringResource(Res.string.collection_issue_card),
                    fontSize = (16 * scale).sp,
                    lineHeight = (19 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CollectionActivityProgressRow(
    label: String,
    count: Long,
    target: Long,
    remaining: Long,
    color: Color,
    scale: Float,
    emotionProgress: List<CollectionEmotionProgress> = emptyList(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp * scale)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.collection_activity_count, label, count, target),
                fontSize = (13 * scale).sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
            )
            Text(
                text =
                    if (remaining == 0L) {
                        stringResource(Res.string.collection_goal_reached)
                    } else {
                        stringResource(Res.string.collection_goal_remaining, remaining)
                    },
                fontSize = (11 * scale).sp,
                color = AppColors.TextSecondary,
            )
        }
        if (emotionProgress.isNotEmpty()) {
            CollectionEmotionProgressIndicator(emotionProgress, target, scale)
        } else {
            LinearProgressIndicator(
                progress = { activityProgress(count, target) },
                modifier = Modifier.fillMaxWidth().height(7.dp * scale),
                color = color,
                trackColor = AppColors.LightDivider,
            )
        }
    }
}

@Composable
private fun CollectionEmotionProgressIndicator(
    progress: List<CollectionEmotionProgress>,
    target: Long,
    scale: Float,
) {
    val segments = progress.map { it.copy(count = it.count.coerceAtMost(target)) }.filter { it.count > 0L }
    val total = segments.sumOf { it.count }
    val filledFraction = if (target <= 0L) 0f else (total.toFloat() / target).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp * scale)
            .clip(RoundedCornerShape(50))
            .background(AppColors.LightDivider),
    ) {
        if (filledFraction > 0f) {
            Row(Modifier.fillMaxWidth(filledFraction).fillMaxHeight()) {
                segments.forEach { segment ->
                    Box(
                        Modifier
                            .weight(segment.count.toFloat())
                            .fillMaxHeight()
                            .background(segment.emotion.representativeColor),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CollectionTomorrowCard(modifier: Modifier = Modifier) {
    val cardDescription = stringResource(Res.string.collection_tomorrow_card_accessibility_description)
    CollectionCardFrame(
        cornerIconTint = AppColors.Primary,
        modifier = modifier.semantics { contentDescription = cardDescription },
    ) { scale ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp * scale, vertical = 26.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp * scale, Alignment.CenterVertically),
        ) {
            Box(contentAlignment = Alignment.Center) {
                val diameter = 78.dp * scale
                val strokeWidth = 2.dp * scale
                val dashLength = 5.dp * scale
                val dashGap = 5.dp * scale
                Canvas(Modifier.size(diameter)) {
                    val strokeWidthPx = strokeWidth.toPx()
                    drawCircle(
                        color = AppColors.Primary,
                        radius = size.minDimension / 2 - strokeWidthPx / 2,
                        style =
                            Stroke(
                                width = strokeWidthPx,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(dashLength.toPx(), dashGap.toPx()),
                                ),
                            ),
                    )
                }
                Text(
                    text = "?",
                    color = AppColors.Primary,
                    fontSize = (42 * scale).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(Res.string.collection_tomorrow_card_message),
                color = AppColors.TextSecondary,
                fontSize = (15 * scale).sp,
                lineHeight = (22 * scale).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun activityProgress(
    count: Long,
    target: Long,
): Float {
    if (target <= 0L) return 1f
    return (count.coerceAtMost(target).toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

@Preview(showBackground = true)
@Composable
private fun CollectionProgressCardPreview() {
    CollectionPreviewTheme {
        CollectionProgressCard(
            progress =
                CollectionProgressUiModel(
                    emotion = EmotionType.FRUSTRATED,
                    stampCount = 3,
                    stampTarget = 1,
                    stampRemaining = 0,
                    pressCount = 70,
                    pressTarget = 100,
                    pressRemaining = 30,
                    canClaim = true,
                    stampProgressByEmotion =
                        listOf(
                            CollectionEmotionProgress(EmotionType.FRUSTRATED, 1),
                            CollectionEmotionProgress(EmotionType.IRRITATED, 1),
                            CollectionEmotionProgress(EmotionType.EXHAUSTED, 1),
                        ),
                ),
            isLoading = false,
            error = null,
            onClaim = {},
            onRetry = {},
            modifier = Modifier.size(300.dp, 400.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionTomorrowCardPreview() {
    CollectionPreviewTheme {
        CollectionTomorrowCard(Modifier.size(300.dp, 400.dp))
    }
}
