package com.pheeeew.core.permission

import androidx.compose.runtime.Composable
import com.pheeeew.core.designsystem.component.AppDialog

@Composable
fun LocationPermissionSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "위치 권한 설정 안내",
        body = "한숨을 별로 만들려면 위치 권한이 필요합니다.\n설정에서 위치 권한을 '허용'으로 변경해주세요.",
        confirmText = "설정으로 이동",
        onConfirmClick = onOpenSettings,
        onDismissRequest = onDismiss,
        onDismissClick = onDismiss,
        dismissText = "취소",
    )
}

@Composable
fun LocationServicesSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "위치 서비스 설정 안내",
        body = "현재 위치를 확인하려면 기기 설정에서 위치 서비스를 켜주세요.",
        confirmText = "설정으로 이동",
        onConfirmClick = onOpenSettings,
        onDismissRequest = onDismiss,
        onDismissClick = onDismiss,
        dismissText = "취소",
    )
}
