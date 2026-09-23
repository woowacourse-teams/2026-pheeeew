package com.pheeeew.legacy.core.permission

import androidx.compose.runtime.Composable
import com.pheeeew.legacy.core.designsystem.component.AppDialog

enum class PermissionSettingsTarget(
    internal val displayName: String,
) {
    Location("위치"),
    Microphone("마이크"),
    LocationAndMicrophone("위치/마이크"),
}

@Composable
fun PermissionSettingsDialog(
    target: PermissionSettingsTarget,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val permissionName = target.displayName
    AppDialog(
        title = "$permissionName 권한 설정 안내",
        body =
            "한숨을 별로 만들기 위해 $permissionName 권한이 필요합니다.\n" +
                "설정에서 $permissionName 권한을 '허용'으로 변경해주세요.",
        confirmText = "설정으로 이동",
        onConfirmClick = onOpenSettings,
        onDismissRequest = onDismiss,
        onDismissClick = onDismiss,
        dismissText = "취소",
    )
}

@Composable
fun LocationPermissionSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    PermissionSettingsDialog(
        target = PermissionSettingsTarget.Location,
        onOpenSettings = onOpenSettings,
        onDismiss = onDismiss,
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
