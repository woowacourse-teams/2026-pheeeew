package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.ranking.press.PressEmotion

@Composable
internal fun PressEmotionFilter(
    selectedEmotion: PressEmotion,
    onEmotionSelected: (PressEmotion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PressEmotion.entries.forEach { emotion ->
            val isSelected = emotion == selectedEmotion
            Box(
                modifier =
                    Modifier
                        .width(72.dp)
                        .height(44.dp)
                        .background(
                            if (isSelected) AppColors.Primary else Color(0xFFF0F0EC),
                            RoundedCornerShape(16.dp),
                        ).then(
                            if (isSelected) {
                                Modifier.border(AppBorders.Standard, AppColors.GroupInk, RoundedCornerShape(16.dp))
                            } else {
                                Modifier
                            },
                        ).clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onEmotionSelected(emotion) },
                        ).semantics { selected = isSelected },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(
                    text = emotion.label,
                    color = AppColors.RankingContent,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(24.dp))
    }
}

@Preview(name = "프레스 감정 필터")
@Composable
private fun PressEmotionFilterPreview() {
    AppTheme {
        PressEmotionFilter(PressEmotion.Frustrated, {}, Modifier.fillMaxWidth())
    }
}
