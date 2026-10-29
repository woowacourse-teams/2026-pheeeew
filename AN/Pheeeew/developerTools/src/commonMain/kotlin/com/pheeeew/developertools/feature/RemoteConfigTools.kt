package com.pheeeew.developertools.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.domain.model.config.AppConfig
import com.pheeeew.domain.model.config.AppConfigSnapshot
import com.pheeeew.domain.model.config.AppConfigSource
import com.pheeeew.domain.model.config.AppConfigState
import com.pheeeew.domain.model.config.isValidNotice
import kotlinx.coroutines.launch

@Composable
internal fun RemoteConfigTools(
    repository: AppConfigRepository,
    onRestart: (() -> Unit) -> Unit,
) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    RemoteConfigToolsContent(
        state = state,
        onRefresh = { scope.launch { repository.refresh() } },
        onClearCache = { scope.launch { repository.clearCache() } },
        onSetOverride = repository::setOverride,
        onApply = { config -> onRestart { repository.setOverride(config) } },
    )
}

@Composable
private fun RemoteConfigToolsContent(
    state: AppConfigState,
    onRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onSetOverride: (AppConfig?) -> Unit,
    onApply: (AppConfig) -> Unit,
) {
    var enabled by remember { mutableStateOf(state.config.dailyPressRankingEnabled) }
    var notice by remember { mutableStateOf(state.config.noticeMessage) }
    Text("원격 설정")
    SelectionContainer {
        Column {
            Text("출처: ${state.source} / 초기화: ${state.initialized}")
            Text("적용값: 순위=${state.config.dailyPressRankingEnabled}, 공지=${state.config.noticeMessage}")
            Text("서버/캐시: ${state.snapshot}")
            Text("서버 변경: ${state.snapshot?.updatedAt ?: "없음"}")
            Text("마지막 수신: ${state.lastFetchSucceededAt ?: "없음"}")
            Text("값 변경 적용: ${state.lastAppliedAt ?: "없음"}")
            Text("오류: ${state.error ?: "없음"} / 저장 실패: ${state.storageError}")
        }
    }
    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
        Text(if (state.refreshing) "받는 중" else "설정 다시 받기")
    }
    TextButton(onClick = onClearCache, enabled = !state.refreshing) {
        Text("원격 설정 캐시만 초기화")
    }
    Text("임시 일일 순위 활성화")
    Switch(checked = enabled, onCheckedChange = { enabled = it })
    OutlinedTextField(value = notice, onValueChange = { notice = it }, label = { Text("임시 공지 (최대 200자)") })
    TextButton(onClick = { onSetOverride(null) }) { Text("덮어쓰기 해제") }
    Text("임시값은 프로세스 재시작 시 제거됩니다. 실제 서버 반영 증거와 구분하세요.")
    HorizontalDivider()
    Spacer(Modifier.height(24.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = notice.isValidNotice(),
        onClick = { onApply(AppConfig(enabled, notice)) },
    ) { Text("적용하기") }
}

@Preview
@Composable
private fun RemoteConfigToolsPreview() {
    MaterialTheme {
        Column {
            RemoteConfigToolsContent(
                state =
                    AppConfigState(
                        initialized = true,
                        snapshot = AppConfigSnapshot(1, true, "공지 미리보기", "2026-10-29T03:00:00Z"),
                        normalSource = AppConfigSource.SERVER,
                        lastFetchSucceededAt = "2026-10-29T03:00:10Z",
                        lastAppliedAt = "2026-10-29T03:00:10Z",
                    ),
                onRefresh = {},
                onClearCache = {},
                onSetOverride = {},
                onApply = {},
            )
        }
    }
}
