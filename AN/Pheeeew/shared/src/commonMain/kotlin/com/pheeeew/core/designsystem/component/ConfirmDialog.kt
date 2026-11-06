package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.AppTypography

@Composable
fun ConfirmDialog(
    title: String,
    content: String,
    confirmText: String,
    cancelText: String? = null,
    onConfirm: () -> Unit,
    onCancel: () -> Unit = {},
    properties: DialogProperties = DialogProperties(),
) {
    AppDialog(onDismissRequest = onCancel, properties = properties) {
        ConfirmDialogContent(title, content, confirmText, cancelText, onConfirm, onCancel)
    }
}

@Composable
private fun ConfirmDialogContent(
    title: String,
    content: String,
    confirmText: String,
    cancelText: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(shape = AppShapes.DetailDialog, color = AppColors.Surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = AppTypography.confirmDialogTitle(), color = AppColors.GroupInk)
            Text(content, style = AppTypography.confirmDialogBody(), color = AppColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cancelText != null) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Gray),
                    ) {
                        Text(cancelText, color = AppColors.TextSecondary)
                    }
                }
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Gray),
                ) {
                    Text(confirmText, color = AppColors.Red)
                }
            }
        }
    }
}

@Preview(name = "작성 중 나가기 경고", widthDp = 360, showBackground = true)
@Composable
private fun ConfirmDialogPreview() {
    ConfirmDialogContent(
        "작성 중인 내용이 있어요.",
        "지금까지 작성하던 내용이 저장되지 않아요.\n나갈까요?",
        "나가기",
        "취소",
        {},
        {},
    )
}

@Preview(name = "앱 시작 오류", widthDp = 360, showBackground = true)
@Composable
private fun AppEntryErrorDialogPreview() {
    AppTheme {
        ConfirmDialogContent(
            title = "앱을 시작하지 못했어요",
            content = "네트워크 상태를 확인한 후 다시 시도해주세요.",
            confirmText = "다시 시도",
            cancelText = null,
            onConfirm = {},
            onCancel = {},
        )
    }
}
