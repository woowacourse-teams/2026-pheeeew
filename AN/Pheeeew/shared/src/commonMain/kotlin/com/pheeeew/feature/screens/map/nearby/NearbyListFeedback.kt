package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import com.pheeeew.feature.component.emotion.face
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.nearby_confirm
import pheeeew.shared.generated.resources.nearby_initial_empty_title
import pheeeew.shared.generated.resources.nearby_leave_emotion
import pheeeew.shared.generated.resources.nearby_load_error
import pheeeew.shared.generated.resources.nearby_next_load_error
import pheeeew.shared.generated.resources.nearby_no_visible_stamps

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
        Text(
            stringResource(Res.string.nearby_initial_empty_title),
            color = FeedbackInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(Res.string.nearby_no_visible_stamps),
            color = FeedbackSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        FeedbackButton(stringResource(Res.string.nearby_leave_emotion), onLeaveEmotion, highlighted = true)
    }
}

@Composable
internal fun NearbyLoadError(
    message: String,
    hasItems: Boolean,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    EmotionListLoadError(
        title =
            stringResource(
                if (hasItems) Res.string.nearby_next_load_error else Res.string.nearby_load_error,
            ),
        message = message,
        hasItems = hasItems,
        onRetry = onRetry,
        modifier = modifier,
    )
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.nearby_confirm), color = FeedbackInk)
            }
        }
    }
}

@Composable
private fun FeedbackButton(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.height(48.dp).raisedButtonBorder(CircleShape, interactionSource = interactionSource),
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (highlighted) Color(0xFFFFE164) else Color.White,
                contentColor = FeedbackInk,
            ),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
@Preview(name = "Nearby · 오류 및 안내", widthDp = 402, showBackground = true)
private fun NearbyFeedbackPreview() {
    AppTheme {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NearbyNotice("잠시 후 다시 시도해 주세요.", onDismiss = {})
            NearbyLoadError("연결 상태를 확인해 주세요.", hasItems = false, onRetry = {})
            NearbyLoadError("연결 상태를 확인해 주세요.", hasItems = true, onRetry = {})
        }
    }
}
