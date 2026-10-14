package com.pheeeew.feature.screens.group.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.detail.component.InviteCodeDialog

@Preview(name = "초대코드 공유", widthDp = 402, heightDp = 815, showBackground = true)
@Composable
private fun InviteCodeDialogPreview() {
    AppTheme {
        InviteCodeDialog(
            code = "H1Y226",
            onShare = {},
            onCopy = {},
            copied = false,
            onDismiss = {},
        )
    }
}

@Preview(name = "초대코드 · 복사 성공", widthDp = 360, heightDp = 800)
@Composable
private fun InviteCodeCopiedPreview() {
    AppTheme {
        InviteCodeDialog(
            code = "H1Y226",
            onShare = {},
            onCopy = {},
            copied = true,
            keepCopySuccessVisible = true,
            onDismiss = {},
        )
    }
}
