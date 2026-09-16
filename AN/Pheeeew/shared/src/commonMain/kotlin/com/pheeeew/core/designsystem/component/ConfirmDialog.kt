package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirmClick: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        ConfirmDialogContent(
            title = title,
            body = body,
            confirmText = confirmText,
            onConfirmClick = onConfirmClick,
            onDismissClick = onDismissClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun ConfirmDialogContent(
    title: String,
    body: String,
    confirmText: String,
    onConfirmClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.surface,
        contentColor = AppTheme.colors.onBackground,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = AppTheme.typography.dialogTitle)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = body,
                style = AppTheme.typography.dialogBody,
                color = AppTheme.colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismissClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "취소",
                        style = AppTheme.typography.button,
                        color = AppTheme.colors.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onConfirmClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = confirmText,
                        style = AppTheme.typography.button,
                        color = AppColors.Red400,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ConfirmDialogOneButtonPreview() {
    AppTheme {
        ConfirmDialogContent(
            title = "인터넷 연결 확인",
            body = "네트워크 연결이 원활하지 않습니다. Wi-Fi 또는 모바일\n데이터 연결 상태를 확인한 후 다시 시도해주세요.",
            confirmText = "확인",
            onConfirmClick = {},
            onDismissClick = {},
        )
    }
}
