package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
fun ConfirmDialog(
    title: String,
    content: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppDialog(onDismissRequest = onCancel) {
        ConfirmDialogContent(title, content, confirmText, cancelText, onConfirm, onCancel)
    }
}

@Composable
private fun ConfirmDialogContent(
    title: String,
    content: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = AppColors.Surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.GroupInk)
            Text(content, fontSize = 14.sp, lineHeight = 22.sp, color = AppColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(cancelText, color = AppColors.TextSecondary)
                }
                TextButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(confirmText, color = Color.Red)
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
