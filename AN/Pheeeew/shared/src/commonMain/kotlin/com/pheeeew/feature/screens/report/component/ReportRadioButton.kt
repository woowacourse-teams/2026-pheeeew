package com.pheeeew.feature.screens.report.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.appBackground
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appPrimary

@Composable
fun ReportRadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.appInk
    val background = if (selected) MaterialTheme.colorScheme.appPrimary else MaterialTheme.colorScheme.appBackground
    Canvas(modifier = modifier.size(22.dp)) {
        drawCircle(
            color = background,
            radius = size.minDimension / 2 - 1.dp.toPx(),
        )
        drawCircle(
            color = ink,
            radius = size.minDimension / 2 - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )
        if (selected) drawCircle(color = ink, radius = 4.dp.toPx())
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
