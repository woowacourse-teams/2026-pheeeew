package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_emotion_accessibility
import pheeeew.shared.generated.resources.group_detail_emotion_count
import pheeeew.shared.generated.resources.group_detail_feedback_plus_one

/** 다섯 감정 버튼과 각 탭에서 발생한 개별 피드백 애니메이션을 표시합니다. */
@Composable
internal fun EmotionPad(
    counts: List<EmotionCountUiModel>,
    enabled: Boolean,
    onEmotionTap: (EmotionKind) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val activeBursts = remember { mutableStateListOf<EmotionBurst>() }
    var nextBurstId by remember { mutableLongStateOf(0L) }
    val countsByKind = remember(counts) { counts.associateBy { it.kind } }

    fun showFeedback(emotion: EmotionKind) {
        val burstId = ++nextBurstId
        activeBursts.add(EmotionFeedbackCatalog.create(burstId, emotion))
    }

    val emotions = EmotionKind.entries
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            emotions.take(3).forEach { emotion ->
                EmotionButton(
                    emotion = emotion,
                    count = countsByKind.getValue(emotion).count,
                    enabled = enabled,
                    activeBursts = activeBursts.filter { it.emotion == emotion },
                    onClick = { if (onEmotionTap(emotion)) showFeedback(emotion) },
                    onBurstFinished = { burstId -> activeBursts.removeAll { it.id == burstId } },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(19.5.dp, Alignment.CenterHorizontally),
        ) {
            emotions.drop(3).forEach { emotion ->
                EmotionButton(
                    emotion = emotion,
                    count = countsByKind.getValue(emotion).count,
                    enabled = enabled,
                    activeBursts = activeBursts.filter { it.emotion == emotion },
                    onClick = { if (onEmotionTap(emotion)) showFeedback(emotion) },
                    onBurstFinished = { burstId -> activeBursts.removeAll { it.id == burstId } },
                )
            }
        }
    }
}

@Composable
private fun EmotionButton(
    emotion: EmotionKind,
    count: Long,
    enabled: Boolean,
    activeBursts: List<EmotionBurst>,
    onClick: () -> Unit,
    onBurstFinished: (Long) -> Unit,
) {
    val emotionName = stringResource(EmotionFeedbackCatalog.name(emotion))
    val countText = formatCount(count)
    val accessibilityText =
        stringResource(
            Res.string.group_detail_emotion_accessibility,
            emotionName,
            formatCount(count),
        )

    Box(modifier = Modifier.size(width = 105.dp, height = 167.dp)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 105.dp, height = 110.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                        .semantics { contentDescription = accessibilityText },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(EmotionFeedbackCatalog.face(emotion)),
                    contentDescription = null,
                    modifier = Modifier.size(width = 105.dp, height = 110.dp),
                )
            }
            Text(
                text = emotionName,
                modifier = Modifier.padding(top = 8.dp),
                color = AppColors.GroupInk,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = countText,
                modifier = Modifier.padding(top = 2.dp),
                color = Color(0xFF7B817B),
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        activeBursts.forEach { burst ->
            key(burst.id) {
                FloatingEmotionFeedback(
                    burst = burst,
                    onFinished = { onBurstFinished(burst.id) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun FloatingEmotionFeedback(
    burst: EmotionBurst,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(burst.id) { Animatable(0f) }
    LaunchedEffect(burst.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = FEEDBACK_DURATION_MILLIS, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }

    val amount = progress.value
    val alpha =
        when {
            amount < FADE_IN_END -> amount / FADE_IN_END
            amount > FADE_OUT_START -> (1f - amount) / (1f - FADE_OUT_START)
            else -> 1f
        }.coerceIn(0f, 1f)

    Box(
        modifier =
            modifier
                .offset(
                    x = burst.horizontalDrift.dp,
                    y = (burst.verticalLaunchOffset - amount * FLOAT_DISTANCE_DP).dp,
                ).graphicsLayer {
                    this.alpha = alpha
                    scaleX = 0.86f + amount * 0.14f
                    scaleY = 0.86f + amount * 0.14f
                },
        contentAlignment = Alignment.Center,
    ) {
        when (burst.stickerKind) {
            EmotionStickerKind.PlusOne -> {
                FloatingTextSticker(
                    text = stringResource(Res.string.group_detail_feedback_plus_one),
                    fillColor = PLUS_ONE_COLOR,
                    rotationDegrees = burst.rotationDegrees,
                    fontSize = 14.sp,
                )
            }

            EmotionStickerKind.Text -> {
                FloatingTextSticker(
                    text = stringResource(EmotionFeedbackCatalog.stickerText(burst.emotion, burst.stickerVariant)),
                    fillColor = EmotionFeedbackCatalog.color(burst.emotion),
                    rotationDegrees = burst.rotationDegrees,
                    fontSize = 13.sp,
                )
            }

            EmotionStickerKind.Emoji -> {
                FloatingEmojiSticker(
                    text = stringResource(EmotionFeedbackCatalog.emoji(burst.emotion, burst.stickerVariant)),
                    rotationDegrees = burst.rotationDegrees,
                )
            }

            EmotionStickerKind.Face -> {
                Image(
                    painter = painterResource(EmotionFeedbackCatalog.face(burst.emotion)),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp).rotate(burst.rotationDegrees),
                )
            }
        }
    }
}

@Composable
private fun FloatingTextSticker(
    text: String,
    fillColor: Color,
    rotationDegrees: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val shape = RoundedCornerShape(11.dp)
    Text(
        text = text,
        modifier =
            Modifier
                .rotate(rotationDegrees)
                .shadow(3.dp, shape)
                .background(fillColor, shape)
                .border(1.5.dp, AppColors.GroupInk, shape)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        color = AppColors.GroupInk,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun FloatingEmojiSticker(
    text: String,
    rotationDegrees: Float,
) {
    Box(
        modifier =
            Modifier
                .size(42.dp)
                .rotate(rotationDegrees)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
                .border(1.5.dp, AppColors.GroupInk, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 23.sp, lineHeight = 25.sp)
    }
}

internal fun formatCount(value: Long): String =
    value
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

private const val FEEDBACK_DURATION_MILLIS = 1_080
private const val FLOAT_DISTANCE_DP = 104f
private const val FADE_IN_END = 0.1f
private const val FADE_OUT_START = 0.72f
private val PLUS_ONE_COLOR = Color(0xFFFFE55C)
