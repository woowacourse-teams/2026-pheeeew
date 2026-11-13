package com.pheeeew.developertools.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun DeveloperToolsHome(
    info: DeveloperToolsInfo,
    onOpenErrorScenarios: () -> Unit,
    onOpenLocalData: () -> Unit,
    onOpenRemoteConfig: () -> Unit,
) {
    Text("개발자 도구", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(24.dp))
    SelectionContainer {
        Column(Modifier.fillMaxWidth()) {
            InfoRow("플랫폼", info.platform)
            InfoRow("앱 버전", info.version)
            InfoRow("빌드 번호", info.buildNumber)
            InfoRow("빌드 구성", info.buildConfiguration)
            InfoRow("서버 환경", info.serverEnvironment)
            InfoRow("API base URL", info.apiBaseUrl)
        }
    }
    Spacer(Modifier.height(32.dp))

    Text("Tools", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))

    ClickableRow("에러 상황 재현", onOpenErrorScenarios)
    HorizontalDivider()
    ClickableRow("로컬 데이터 설정", onOpenLocalData)
    HorizontalDivider()
    ClickableRow("원격 설정", onOpenRemoteConfig)
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}

@Composable
private fun ClickableRow(
    text: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = Color.Black)
            Text(">")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeveloperToolsHomePreview() {
    MaterialTheme {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            DeveloperToolsHome(
                info = DeveloperToolsInfo("Android", "2.5.0", "13", "Debug", "개발", "https://example.com"),
                onOpenErrorScenarios = {},
                onOpenLocalData = {},
                onOpenRemoteConfig = {},
            )
        }
    }
}
