package com.pheeeew.feature.screens.ranking.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
fun WeekSelector(
    week: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
) {
    val fontScale = LocalDensity.current.fontScale

    Row(
        modifier =
            modifier
                .widthIn(max = 284.dp * fontScale)
                .fillMaxWidth()
                .height(36.dp)
                .background(AppColors.RankingSurface, RoundedCornerShape(24.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeekArrow(isPrevious = true, enabled = canGoPrevious, onClick = onPrevious)
        Text(
            text = week,
            color = AppColors.RankingContent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        WeekArrow(isPrevious = false, enabled = canGoNext, onClick = onNext)
    }
}

@Composable
private fun WeekArrow(
    isPrevious: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val arrowModifier =
        Modifier
            .size(width = 44.dp, height = 36.dp)
            .clickable(
                enabled = enabled,
                onClickLabel = if (isPrevious) "이전 주" else "다음 주",
                onClick = onClick,
            ).padding(horizontal = 15.dp, vertical = 11.dp)

    val arrowColor = AppColors.RankingContent.copy(alpha = if (enabled) 1f else 0.35f)

    Canvas(arrowModifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val halfArrow = 4.dp.toPx()
        val tipX = if (isPrevious) centerX - 2.dp.toPx() else centerX + 2.dp.toPx()
        val tailX = if (isPrevious) tipX + halfArrow else tipX - halfArrow
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(
            arrowColor,
            Offset(tailX, centerY - halfArrow),
            Offset(tipX, centerY),
            stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            arrowColor,
            Offset(tipX, centerY),
            Offset(tailX, centerY + halfArrow),
            stroke.width,
            cap = stroke.cap,
        )
    }
}

@Preview
@Composable
private fun WeekSelectorPreview() {
    WeekSelector("2026.09.16 ~ 2026.09.23", {}, {}, Modifier.padding(16.dp))
}
