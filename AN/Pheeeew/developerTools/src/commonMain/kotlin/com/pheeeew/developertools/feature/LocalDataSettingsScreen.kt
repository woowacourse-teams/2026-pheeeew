package com.pheeeew.developertools.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun LocalDataSettingsScreen(
    canClearConfig: Boolean,
    canResetNotificationPermission: Boolean,
    applying: Boolean,
    error: String?,
    onApply: (Boolean, Boolean, Boolean) -> Unit,
) {
    var onboarding by rememberSaveable { mutableStateOf(false) }
    var config by rememberSaveable { mutableStateOf(false) }
    var notificationPermission by rememberSaveable { mutableStateOf(false) }
    Text("로컬 데이터 설정", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text("초기화할 항목을 선택해 주세요.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = onboarding, onCheckedChange = { onboarding = it }, enabled = !applying)
        Column {
            Text("온보딩 열람 기록")
            Text("초기화 후 온보딩 화면을 다시 표시합니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = config, onCheckedChange = { config = it }, enabled = canClearConfig && !applying)
        Column {
            Text("원격 설정 캐시")
            Text("저장된 설정을 지웁니다. 임시 덮어쓰기는 유지됩니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (canResetNotificationPermission) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = notificationPermission,
                onCheckedChange = { notificationPermission = it },
                enabled = !applying,
            )
            Column {
                Text("알림 권한 테스트 상태")
                Text(
                    "앱 요청 이력을 지웁니다. 허용 상태의 OS 권한 회수는 앱이 백그라운드에 있을 때 시스템이 안전하다고 판단하면 적용됩니다. 권한 회수 후 앱을 다시 열어 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "이전에 거부했다면 시스템 거부 이력이 남을 수 있습니다. 최초 요청을 다시 확인하려면 권한을 회수하고 ADB에서 user-set·user-fixed 기록도 초기화하세요.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("다른 로컬 데이터", style = MaterialTheme.typography.titleMedium)
    Text(
        "기기 인증 정보, 알림 토큰·동기화 기록, 알림 클릭·재방문 기록은 " +
            "앱에서 관리 중이며 이 화면에서 초기화할 수 없습니다.",
        style = MaterialTheme.typography.bodySmall,
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(24.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = (onboarding || config || notificationPermission) && !applying,
        onClick = { onApply(onboarding, config, notificationPermission) },
    ) { Text(if (applying) "적용 중" else "적용하기") }
}

@Preview
@Composable
private fun LocalDataSettingsScreenPreview() {
    MaterialTheme {
        Column {
            LocalDataSettingsScreen(
                canClearConfig = true,
                canResetNotificationPermission = true,
                applying = false,
                error = null,
                onApply = { _, _, _ -> },
            )
        }
    }
}
