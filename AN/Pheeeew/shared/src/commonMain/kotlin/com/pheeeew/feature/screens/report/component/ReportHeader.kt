package com.pheeeew.feature.screens.report.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.component.BasicTopBar
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
fun ReportHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BasicTopBar(
        title = "신고",
        onBack = onBack,
        modifier = modifier,
        enabled = enabled,
        backContentDescription = "뒤로",
        titleColor = AppColors.GroupInk,
    )
}

@Preview(name = "신고 헤더")
@Composable
private fun ReportHeaderPreview() {
    ReportHeader(onBack = {})
}
