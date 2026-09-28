package com.pheeeew.feature.screens.map.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders

@Composable
internal fun EmotionPromptLabel(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(100.dp)

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(Color.White)
                .border(width = AppBorders.Standard, color = Color(0xFF292B2A), shape = shape)
                .padding(horizontal = 16.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isExpanded) "지금 감정이 어때요?" else "터치해서 감정을 꺼내보세요",
            color = Color(0xFF292B2A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview(name = "접힌 감정 안내", showBackground = true)
@Composable
fun EmotionPromptLabelCollapsedPreview() {
    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        EmotionPromptLabel(isExpanded = false)
    }
}

@Preview(name = "펼친 감정 안내", showBackground = true)
@Composable
fun EmotionPromptLabelExpandedPreview() {
    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        EmotionPromptLabel(isExpanded = true)
    }
}
