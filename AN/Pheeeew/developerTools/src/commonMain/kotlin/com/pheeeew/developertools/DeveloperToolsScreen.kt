package com.pheeeew.developertools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeveloperToolsScreen(
    info: DeveloperToolsInfo,
    onBack: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                TextButton(onClick = onBack) { Text("뒤로") }
                Spacer(Modifier.height(16.dp))
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
            }
        }
    }
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
