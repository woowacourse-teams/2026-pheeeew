package com.pheeeew.feature.screens.report.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppTypography
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.report_submit

@Composable
fun ReportSubmitButton(
    enabled: Boolean,
    isSubmitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = AppShapes.Action
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        enabled = enabled,
        shape = shape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                contentColor = AppColors.TextPrimary,
                disabledContainerColor = AppColors.Primary,
                disabledContentColor = AppColors.TextPrimary.copy(alpha = 0.55f),
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .height(
                    56.dp,
                ).raisedButtonBorder(shape, interactionSource = interactionSource, elevated = enabled),
    ) {
        if (isSubmitting) {
            CircularLoadingIndicator(
                modifier = Modifier.height(20.dp),
                color = AppColors.TextPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = stringResource(Res.string.report_submit),
                style = AppTypography.reportSubmitLabel(),
            )
        }
    }
}

@Preview(name = "신고 제출 버튼")
@Composable
private fun ReportSubmitButtonPreview() {
    ReportSubmitButton(enabled = true, isSubmitting = false, onClick = {})
}
