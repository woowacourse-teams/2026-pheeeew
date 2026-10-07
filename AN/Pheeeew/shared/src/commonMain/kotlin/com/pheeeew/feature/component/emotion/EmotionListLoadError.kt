package com.pheeeew.feature.component.emotion

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.emotion.EmotionState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_list_retry

private val ErrorInk = Color(0xFF252826)
private val ErrorSecondary = Color(0xFF70766F)

@Composable
internal fun EmotionListLoadError(
    title: String,
    message: String,
    hasItems: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
                Image(
                    painter = painterResource(EmotionState.FRUSTRATED.face),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(title, color = ErrorInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = ErrorSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            EmotionListRetryButton(onRetry)
        }
    }
}

@Composable
private fun EmotionListRetryButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .height(48.dp)
                .raisedButtonBorder(CircleShape, interactionSource = interactionSource),
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = ErrorInk,
            ),
    ) {
        Text(stringResource(Res.string.emotion_list_retry), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(name = "감정 목록 · 오류", widthDp = 360, showBackground = true)
@Composable
private fun EmotionListLoadErrorPreview() {
    AppTheme {
        EmotionListLoadError(
            title = "감정을 불러오지 못했어요",
            message = "연결 상태를 확인해 주세요.",
            hasItems = false,
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
