package com.pheeeew.feature.screens.ranking.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
) {
    val fontScale = LocalDensity.current.fontScale

    Row(
        modifier =
            modifier
                .widthIn(max = 230.dp * fontScale)
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(AppColors.RankingSurface, RoundedCornerShape(24.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeekArrow(isPrevious = true, onClick = onPrevious)
        Text(
            text = week,
            color = AppColors.RankingContent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        WeekArrow(isPrevious = false, onClick = onNext)
    }
}

@Composable
private fun WeekArrow(
    isPrevious: Boolean,
    onClick: () -> Unit,
) {
    val arrowModifier =
        Modifier
            .size(44.dp)
            .clickable(
                onClickLabel = if (isPrevious) "이전 주" else "다음 주",
                onClick = onClick,
            ).padding(15.dp)

    Canvas(arrowModifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val halfArrow = 4.dp.toPx()
        val tipX = if (isPrevious) centerX - 2.dp.toPx() else centerX + 2.dp.toPx()
        val tailX = if (isPrevious) tipX + halfArrow else tipX - halfArrow
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(
            AppColors.RankingContent,
            Offset(tailX, centerY - halfArrow),
            Offset(tipX, centerY),
            stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            AppColors.RankingContent,
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
