package com.pheeeew.feature.screens.pressevent

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppTheme

internal fun eventTimerText(remainingMillis: Long): String {
    val hundredths = (remainingMillis.coerceAtLeast(0L) + 9L) / 10L
    return "${(hundredths / 100L).toString().padStart(2, '0')}:${(hundredths % 100L).toString().padStart(2, '0')}"
}

@Composable
internal fun EventTimer(
    remainingMillis: Long,
    color: Color,
) {
    Text(
        eventTimerText(remainingMillis),
        fontSize = 32.sp,
        fontWeight = FontWeight.Black,
        color = color,
    )
}

@Preview(name = "이벤트 타이머")
@Composable
private fun EventTimerPreview() {
    AppTheme { EventTimer(12_340L, Color.White) }
}
