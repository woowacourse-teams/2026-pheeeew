package com.pheeeew.developertools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
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
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.domain.model.config.AppConfig
import kotlinx.coroutines.launch

@Composable
internal fun RemoteConfigTools(repository: AppConfigRepository) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
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
    TextButton(onClick = { scope.launch { repository.refresh() } }, enabled = !state.refreshing) {
        Text(if (state.refreshing) "받는 중" else "설정 다시 받기")
    }
    TextButton(onClick = { scope.launch { repository.clearCache() } }, enabled = !state.refreshing) {
        Text("원격 설정 캐시만 초기화")
    }
    Text("임시 일일 순위 활성화")
    Switch(checked = enabled, onCheckedChange = { enabled = it })
    OutlinedTextField(value = notice, onValueChange = { notice = it }, label = { Text("임시 공지 (최대 200자)") })
    TextButton(
        enabled = notice.count { !it.isLowSurrogate() } <= 200,
        onClick = { repository.setOverride(AppConfig(enabled, notice)) },
    ) { Text("이 프로세스에 임시 적용") }
    TextButton(onClick = { repository.setOverride(null) }) { Text("덮어쓰기 해제") }
    Text("임시값은 프로세스 재시작 시 제거됩니다. 실제 서버 반영 증거와 구분하세요.")
    HorizontalDivider()
}
