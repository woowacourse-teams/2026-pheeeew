package com.pheeeew.feature.map.sighlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme

@Composable
internal fun SighActionSheet(
    onReportClick: () -> Unit,
    onBlockClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 10.dp),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Navy850,
            contentColor = AppColors.Cream100,
            shadowElevation = 12.dp,
        ) {
            Column {
                SighActionItem(
                    text = "신고하기",
                    color = AppColors.Red400,
                    onClick = onReportClick,
                )
                HorizontalDivider(color = AppColors.Cream100.copy(alpha = 0.08f))
                SighActionItem(
                    text = "차단하기",
                    color = AppColors.Cream100.copy(alpha = 0.82f),
                    onClick = onBlockClick,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.padding(horizontal = 10.dp),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.Navy850,
            contentColor = AppColors.Cream100,
            shadowElevation = 12.dp,
        ) {
            SighActionItem(
                text = "취소",
                color = AppColors.Cream100,
                onClick = onCancelClick,
            )
        }
    }
}

@Composable
private fun SighActionItem(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTheme.typography.menuItem.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
@Preview
private fun SighActionSheetPreview() {
    SighActionSheet(
        onReportClick = {},
        onBlockClick = {},
        onCancelClick = {},
    )
}
