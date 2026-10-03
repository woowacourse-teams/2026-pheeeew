package com.pheeeew.feature.screens.report.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.component.BasicTopBar
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.report_title

@Composable
fun ReportHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BasicTopBar(
        title = stringResource(Res.string.report_title),
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
