package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.domain.model.emotion.EmotionState
import org.jetbrains.compose.resources.painterResource

private val FeedbackInk = Color(0xFF252826)
private val FeedbackSecondary = Color(0xFF70766F)

@Composable
internal fun NearbyEmptyState(
    onLeaveEmotion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(EmotionState.FRUSTRATED, EmotionState.EXHAUSTED, EmotionState.IRRITATED)
                .forEachIndexed { index, emotion ->
                    Image(
                        painterResource(emotion.face),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp).graphicsLayer { rotationZ = (index - 1) * 10f },
                    )
                }
        }
        Text("여기에 첫 마음을 남겨볼까요?", color = FeedbackInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "아직 이곳에 보이는 스탬프가 없어요.\n지금의 감정을 가볍게 찍어보세요.",
            color = FeedbackSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        FeedbackButton("내 감정 남기기", onLeaveEmotion, highlighted = true)
    }
}

@Composable
internal fun NearbyLoadError(
    message: String,
    hasItems: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            if (!hasItems) {
                Box(
                    Modifier.size(32.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("!", color = FeedbackSecondary, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            }
            Text(
                if (hasItems) "다음 감정을 불러오지 못했어요" else "감정을 불러오지 못했어요",
                color = FeedbackInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(message, color = FeedbackSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            FeedbackButton("다시 불러오기", onRetry)
        }
    }
}

@Composable
internal fun NearbyNotice(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = Color(0xFFF3F0E8),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, Modifier.weight(1f), color = FeedbackInk, fontSize = 13.sp, lineHeight = 18.sp)
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("확인", color = FeedbackInk) }
        }
    }
}

@Composable
private fun FeedbackButton(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (highlighted) Color(0xFFFFE164) else Color.White,
                contentColor = FeedbackInk,
            ),
        border = BorderStroke(AppBorders.Standard, if (highlighted) FeedbackInk else Color(0xFFD8DDD5)),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}
