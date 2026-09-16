package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.pheeeew.core.designsystem.theme.AppTheme

/**
 * 공통 다이얼로그 컴포넌트.
 *
 * [dismissText]가 주어지면 취소/확인 2버튼 레이아웃(확인 버튼 강조)으로,
 * 생략되면 확인 1버튼 레이아웃(텍스트 버튼)으로 표시됩니다.
 */
@Composable
fun AppDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirmClick: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                usePlatformDefaultWidth = true,
            ),
    ) {
        AppDialogContent(
            title = title,
            body = body,
            confirmText = confirmText,
            onConfirmClick = onConfirmClick,
            onDismissClick = onDismissClick,
            modifier = modifier,
            dismissText = dismissText,
        )
    }
}

@Composable
private fun AppDialogContent(
    title: String,
    body: String,
    confirmText: String,
    onConfirmClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.surface,
        contentColor = AppTheme.colors.onBackground,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = title, style = AppTheme.typography.dialogTitle)
            Box(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                style = AppTheme.typography.dialogBody,
                color = AppTheme.colors.onSurfaceVariant,
            )
            Box(modifier = Modifier.height(24.dp))

            if (dismissText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismissClick) {
                        Text(
                            text = dismissText,
                            style = AppTheme.typography.button,
                            color = AppTheme.colors.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onConfirmClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.danger,
                                contentColor = AppTheme.colors.onDanger,
                            ),
                    ) {
                        Text(text = confirmText, style = AppTheme.typography.button)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onConfirmClick) {
                        Text(
                            text = confirmText,
                            style = AppTheme.typography.button,
                            color = AppTheme.colors.danger,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppDialogTwoButtonPreview() {
    AppTheme {
        AppDialogContent(
            title = "위치 권한 설정 안내",
            body = "서비스를 이용하려면 위치 권한이 필요합니다.\n[설정 > 권한 > 위치]에서 권한을 '허용'으로 변경해주세요.",
            confirmText = "설정으로 이동",
            onConfirmClick = {},
            onDismissClick = {},
            dismissText = "취소",
        )
    }
}

@Preview
@Composable
private fun AppDialogOneButtonPreview() {
    AppTheme {
        AppDialogContent(
            title = "인터넷 연결 확인",
            body = "네트워크 연결이 원활하지 않습니다. Wi-Fi 또는 모바일\n데이터 연결 상태를 확인한 후 다시 시도해주세요.",
            confirmText = "확인",
            onConfirmClick = {},
            onDismissClick = {},
        )
    }
}

@Preview
@Composable
private fun RequiredUpdateAppDialogPreview() {
    AppTheme {
        AppDialogContent(
            title = "앱 업데이트가 필요해요",
            body = "현재 버전은 더 이상 지원되지 않아요. 최신 버전으로 업데이트한 뒤 이용해 주세요.",
            confirmText = "업데이트",
            onConfirmClick = {},
            onDismissClick = {},
            dismissText = "다시 확인",
        )
    }
}
