package com.pheeeew.feature.screens.report.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
fun ReportRadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(22.dp)) {
        drawCircle(
            color = if (selected) AppColors.Primary else AppColors.Background,
            radius = size.minDimension / 2 - 1.dp.toPx(),
        )
        drawCircle(
            color = AppColors.GroupInk,
            radius = size.minDimension / 2 - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )
        if (selected) drawCircle(color = AppColors.GroupInk, radius = 4.dp.toPx())
    }
}

@Preview(name = "선택된 라디오 버튼")
@Composable
private fun ReportRadioButtonSelectedPreview() {
    ReportRadioButton(selected = true)
}

@Preview(name = "선택되지 않은 라디오 버튼")
@Composable
private fun ReportRadioButtonUnselectedPreview() {
    ReportRadioButton(selected = false)
}
