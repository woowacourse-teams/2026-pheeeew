package com.pheeeew.feature.screens.report.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
fun ReportSubmitButton(
    enabled: Boolean,
    isSubmitting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                contentColor = AppColors.TextPrimary,
                disabledContainerColor = AppColors.Primary,
                disabledContentColor = AppColors.TextPrimary.copy(alpha = 0.55f),
            ),
        modifier = modifier.fillMaxWidth().height(56.dp).border(2.5.dp, AppColors.GroupInk, shape),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                color = AppColors.TextPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = "신고하고 동네 지키기",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(name = "신고 제출 버튼")
@Composable
private fun ReportSubmitButtonPreview() {
    ReportSubmitButton(enabled = true, isSubmitting = false, onClick = {})
}
