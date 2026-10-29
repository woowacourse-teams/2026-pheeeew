package com.pheeeew.developertools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.developertools.simulation.ErrorSimulation
import com.pheeeew.developertools.simulation.ErrorSimulationMode
import com.pheeeew.developertools.simulation.ErrorSimulationScenario
import com.pheeeew.developertools.simulation.ErrorSimulationState
import com.pheeeew.developertools.simulation.LocationSimulationMode
import com.pheeeew.developertools.simulation.LocationSimulationSettings
import com.pheeeew.developertools.simulation.NetworkRecovery

@Composable
fun DeveloperToolsScreen(
    info: DeveloperToolsInfo,
    showErrorScenarios: Boolean,
    onOpenErrorScenarios: () -> Unit,
    onBack: () -> Unit,
    onResetOnboarding: () -> Unit,
    onRestart: (() -> Unit) -> Unit,
    onStartRecovery: (Long?) -> Unit,
    configRepository: AppConfigRepository? = null,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showErrorScenarios) {
                LaunchedEffect(Unit) { ErrorSimulation.enableDeveloperTools() }
            }
            Column(
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                TextButton(onClick = onBack) {
                    Text(if (showErrorScenarios) "‹ 개발자 도구" else "뒤로")
                }
                Spacer(Modifier.height(16.dp))
                if (showErrorScenarios) {
                    ErrorSimulationScreen(onRestart = onRestart, onStartRecovery = onStartRecovery)
                } else {
                    DeveloperToolsHome(
                        info = info,
                        configRepository = configRepository,
                        onOpenErrorScenarios = onOpenErrorScenarios,
                        onResetOnboarding = { onRestart(onResetOnboarding) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperToolsHome(
    configRepository: AppConfigRepository?,
    info: DeveloperToolsInfo,
    onOpenErrorScenarios: () -> Unit,
    onResetOnboarding: () -> Unit,
) {
    configRepository?.let { RemoteConfigTools(it) }
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
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenErrorScenarios,
    ) {
        Text("에러 상황 재현")
        Spacer(Modifier.weight(1f))
        Text("›")
    }
    Spacer(Modifier.height(24.dp))
    Text("로컬 기록 초기화", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "온보딩 열람 기록을 지우고 온보딩 화면으로 돌아갑니다.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onResetOnboarding,
    ) {
        Text("온보딩 열람 기록 초기화")
    }
}

@Composable
private fun ErrorSimulationScreen(
    onRestart: (() -> Unit) -> Unit,
    onStartRecovery: (Long?) -> Unit,
) {
    val simulation by ErrorSimulation.state.collectAsState()
    var editor by remember { mutableStateOf(SimulationDraft(simulation)) }
    val draft = editor.resolve(simulation)
    val updateDraft: (ErrorSimulationState) -> Unit = { editor = SimulationDraft(simulation, it) }
    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    val hasChanges = draft.copy(developerToolsEnabled = simulation.developerToolsEnabled) != simulation

    Text("에러 상황 재현", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "조건을 선택하고 완료하면 앱 화면을 초기화합니다. 앱 프로세스와 저장된 정보는 유지됩니다.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = {
                updateDraft(ErrorSimulation.defaultState())
            },
        ) { Text("재현 조건 초기화") }
    }
    Text(
        "현재 적용: ${simulation.summary().replace("\n", " · ")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (hasChanges) {
        Text(
            "선택한 조건은 적용 전까지 사용되지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    NetworkSimulationControls(
        offline = draft.offline,
        delayMillis = draft.networkDelayMillis,
        neverResponds = draft.networkNeverResponds,
        onOfflineChange = { updateDraft(draft.copy(offline = it)) },
        onProfileChange = {
            delay,
            never,
            ->
            updateDraft(draft.copy(networkDelayMillis = delay, networkNeverResponds = never))
        },
    )
    NetworkRecoveryControls(simulation, canStart = !hasChanges, onStart = onStartRecovery)
    Spacer(Modifier.height(24.dp))
    LocationSimulationControls(
        location = draft.location,
        onModeChange = { updateDraft(draft.copy(location = draft.location.copy(mode = it))) },
        onProfileChange = { delay, never ->
            updateDraft(draft.copy(location = draft.location.copy(delayMillis = delay, neverResponds = never)))
        },
        onCoordinatesChange = { latitude, longitude ->
            updateDraft(
                draft.copy(
                    location =
                        draft.location.copy(
                            mode = LocationSimulationMode.FIXED,
                            latitude = latitude,
                            longitude = longitude,
                        ),
                ),
            )
        },
    )
    Spacer(Modifier.height(24.dp))
    Text("개별 에러", style = MaterialTheme.typography.titleLarge)
    Text(
        "녹음 업로드 실패는 업로드 URL 요청 단계, 감정 등록 실패는 합성 서버 오류 응답을 재현합니다. 지도 스타일 로드 실패는 실제 지도에 실패 스타일 주소를 전달하며, 렌더러 생성 실패나 타일 장애는 재현하지 않습니다.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))

    ErrorSimulationScenario.entries.forEach { scenario ->
        val mode = draft.modes[scenario]
        val status = mode?.status ?: "해제됨"

        SimulationRow(title = scenario.title, status = status) {
            if (mode != null) {
                TextButton(onClick = {
                    updateDraft(draft.copy(modes = draft.modes - scenario))
                }) { Text("해제") }
            } else {
                TextButton(onClick = {
                    updateDraft(draft.copy(modes = draft.modes + (scenario to ErrorSimulationMode.NEXT)))
                }) { Text("다음 1회") }
                TextButton(onClick = {
                    updateDraft(draft.copy(modes = draft.modes + (scenario to ErrorSimulationMode.ALWAYS)))
                }) { Text("계속 실패") }
            }
        }
    }

    Button(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        enabled = hasChanges,
        onClick = { showApplyDialog = true },
    ) { Text("적용") }

    if (showApplyDialog) {
        AlertDialog(
            onDismissRequest = { showApplyDialog = false },
            title = { Text("선택한 재현 조건을 적용할까요?") },
            text = {
                Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                    Text(draft.summary())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "완료하면 선택한 조건을 적용하고 앱 화면을 초기화합니다. 화면 이동과 작성 중인 입력, UI 조회 캐시는 초기화됩니다. 인증 정보·영구 저장소·프로세스 작업은 유지됩니다.",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selected = editor.resolve(ErrorSimulation.state.value)
                    showApplyDialog = false
                    onRestart { ErrorSimulation.replaceSettings(selected) }
                }) { Text("완료") }
            },
            dismissButton = {
                TextButton(onClick = { showApplyDialog = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun NetworkRecoveryControls(
    applied: ErrorSimulationState,
    canStart: Boolean,
    onStart: (Long?) -> Unit,
) {
    val run by NetworkRecovery.controller.run.collectAsState()
    var restoreAfterMillis by rememberSaveable { mutableStateOf<Long?>(5_000L) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val options = listOf(5_000L to "5초 후", 10_000L to "10초 후", 30_000L to "30초 후", null to "수동 복구")
    Spacer(Modifier.height(16.dp))
    Text("네트워크 복구 시나리오", style = MaterialTheme.typography.titleMedium)
    Text(
        "현재 화면과 요청을 유지한 채 강제 오프라인으로 전환합니다. 지도 이동·상세 조회 등으로 실패를 확인한 뒤 실제 연결 사용으로 복귀합니다. 네트워크 지연·응답 없음은 해제하며 위치 조건은 유지합니다.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (run != null) {
        SimulationRow("진행 상태", "강제 오프라인 · 복구 대기 중") {
            TextButton(onClick = NetworkRecovery.controller::restore) { Text("지금 복구") }
        }
    } else {
        ConditionDropdown(
            title = "실제 연결로 복귀",
            selected = options.first { it.first == restoreAfterMillis }.second,
            options = options.map { it.second },
            onSelect = { restoreAfterMillis = options[it].first },
        )
        OutlinedButton(
            onClick = { showDialog = true },
            enabled = canStart && applied.modes.isEmpty(),
        ) { Text("현재 화면에서 시나리오 실행") }
        if (!canStart || applied.modes.isNotEmpty()) {
            Text("편집 중인 조건을 적용하고 개별 에러 조건을 모두 해제한 뒤 실행해 주세요.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("현재 화면에서 복구 시나리오를 실행할까요?") },
            text = {
                Text(
                    "강제 오프라인 → ${options.first {
                        it.first == restoreAfterMillis
                    }.second} 실제 연결 사용으로 복귀합니다. 화면·입력·진행 중 요청은 초기화하지 않습니다. 실제 기기가 오프라인이면 복귀 후에도 연결되지 않습니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onStart(restoreAfterMillis)
                }) { Text("실행") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun NetworkSimulationControls(
    offline: Boolean,
    delayMillis: Long,
    neverResponds: Boolean,
    onOfflineChange: (Boolean) -> Unit,
    onProfileChange: (Long, Boolean) -> Unit,
) {
    Text("네트워크 상태", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    SimulationRow(
        title = "연결 상태",
        status = if (offline) "강제 오프라인 적용 중" else "실제 연결 상태 사용",
    ) {
        TextButton(onClick = { onOfflineChange(!offline) }) {
            Text(if (offline) "해제" else "강제 오프라인")
        }
    }
    DelayProfileControl(
        title = "네트워크 응답",
        delayMillis = delayMillis,
        neverResponds = neverResponds,
        onSelect = onProfileChange,
    )
    Text(
        "전송 전 대기 중 조건이 바뀌면 기존 대기를 취소하고 새 조건으로 다시 대기합니다. 오프라인은 즉시 차단하고, 이미 전송된 요청은 기존 제한 시간과 취소 규칙을 따릅니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("지도 배경과 타일 요청은 이 설정의 적용 대상이 아닙니다.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun LocationSimulationControls(
    location: LocationSimulationSettings,
    onModeChange: (LocationSimulationMode) -> Unit,
    onProfileChange: (Long, Boolean) -> Unit,
    onCoordinatesChange: (Double, Double) -> Unit,
) {
    Text("현재 위치 상태", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    val modeOptions =
        listOf(
            LocationSimulationMode.ACTUAL to "실제 위치 사용",
            LocationSimulationMode.FIXED to "지정 좌표 사용",
            LocationSimulationMode.PERMISSION_DENIED to "권한 거부",
            LocationSimulationMode.SERVICES_DISABLED to "위치 서비스 꺼짐",
            LocationSimulationMode.UNAVAILABLE to "위치 확인 실패",
        )
    val modeLabel = modeOptions.first { it.first == location.mode }.second
    ConditionDropdown(
        title = "위치 결과",
        selected = modeLabel,
        options = modeOptions.map { it.second },
        onSelect = { onModeChange(modeOptions[it].first) },
    )
    if (location.mode == LocationSimulationMode.FIXED) {
        FixedCoordinateControl(location.latitude, location.longitude, onCoordinatesChange)
    }
    DelayProfileControl(
        title = "위치 응답",
        delayMillis = location.delayMillis,
        neverResponds = location.neverResponds,
        enabled =
            location.mode != LocationSimulationMode.PERMISSION_DENIED &&
                location.mode != LocationSimulationMode.SERVICES_DISABLED,
        onSelect = onProfileChange,
    )
    Text(
        "위치 획득 시작 시 조건을 고정합니다. 변경은 다음 요청부터 적용되며 완료 시 기존 UI 요청은 취소됩니다. 응답 없음은 실제 저장소의 30초 제한 시간을 따릅니다. 권한·서비스 거부에는 획득 지연을 적용하지 않습니다. 지정 좌표는 OS 위치 권한을 변경하지 않습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FixedCoordinateControl(
    latitude: Double,
    longitude: Double,
    onCoordinatesChange: (Double, Double) -> Unit,
) {
    var latitudeText by rememberSaveable { mutableStateOf(latitude.toString()) }
    var longitudeText by rememberSaveable { mutableStateOf(longitude.toString()) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(latitude, longitude) {
        latitudeText = latitude.toString()
        longitudeText = longitude.toString()
    }
    OutlinedTextField(
        value = latitudeText,
        onValueChange = { latitudeText = it },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text("위도") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    OutlinedTextField(
        value = longitudeText,
        onValueChange = { longitudeText = it },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text("경도") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = {
                val latitudeValue = latitudeText.toDoubleOrNull() ?: Double.NaN
                val longitudeValue = longitudeText.toDoubleOrNull() ?: Double.NaN
                val isValid =
                    latitudeValue.isFinite() && latitudeValue in -90.0..90.0 &&
                        longitudeValue.isFinite() && longitudeValue in -180.0..180.0
                if (isValid) onCoordinatesChange(latitudeValue, longitudeValue)
                validationMessage = if (isValid) null else "위도는 -90~90, 경도는 -180~180 범위로 입력해 주세요."
            },
        ) {
            Text("좌표 선택")
        }
    }
    validationMessage?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DelayProfileControl(
    title: String,
    delayMillis: Long,
    neverResponds: Boolean,
    enabled: Boolean = true,
    onSelect: (Long, Boolean) -> Unit,
) {
    val selected =
        when {
            neverResponds -> "응답 없음"
            delayMillis == 0L -> "없음"
            delayMillis == 500L -> "500ms"
            delayMillis == 2_000L -> "2초"
            delayMillis == 5_000L -> "5초"
            else -> "직접 입력 (${delayMillis}ms)"
        }
    var customDelayText by rememberSaveable(title) { mutableStateOf(delayMillis.toString()) }
    var validationMessage by rememberSaveable(title) { mutableStateOf<String?>(null) }
    LaunchedEffect(delayMillis) { customDelayText = delayMillis.toString() }
    ConditionDropdown(
        title = title,
        selected = selected,
        options = listOf("없음", "500ms", "2초", "5초", "응답 없음", "직접 입력"),
        enabled = enabled,
        onSelect = { index ->
            validationMessage = null
            when (index) {
                0 -> onSelect(0L, false)
                1 -> onSelect(500L, false)
                2 -> onSelect(2_000L, false)
                3 -> onSelect(5_000L, false)
                4 -> onSelect(0L, true)
                else -> Unit
            }
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = customDelayText,
            onValueChange = { customDelayText = it.filter(Char::isDigit) },
            modifier = Modifier.weight(1f),
            label = { Text("직접 입력 (ms)") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TextButton(
            enabled = enabled,
            onClick = {
                val value = customDelayText.toLongOrNull()
                if (value == null || value !in 0L..MAX_CUSTOM_DELAY_MILLIS) {
                    validationMessage = "0~${MAX_CUSTOM_DELAY_MILLIS}ms 범위로 입력해 주세요."
                } else {
                    onSelect(value, false)
                    validationMessage = null
                }
            },
        ) {
            Text("선택")
        }
    }
    validationMessage?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ConditionDropdown(
    title: String,
    selected: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private const val MAX_CUSTOM_DELAY_MILLIS = 300_000L

@Composable
private fun SimulationRow(
    title: String,
    status: String,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
    HorizontalDivider()
}

private val ErrorSimulationMode.status: String
    get() =
        when (this) {
            ErrorSimulationMode.NEXT -> "다음 요청에서 1회 재현"
            ErrorSimulationMode.ALWAYS -> "계속 실패 적용 중"
        }

private fun ErrorSimulationState.summary(): String =
    buildString {
        appendLine("네트워크: ${if (offline) "강제 오프라인" else "실제 연결 사용"}")
        appendLine("네트워크 응답: ${delayLabel(networkDelayMillis, networkNeverResponds)}")
        appendLine("현재 위치: ${location.mode.label}")
        if (location.mode == LocationSimulationMode.FIXED) {
            appendLine("지정 좌표: ${location.latitude}, ${location.longitude}")
        }
        appendLine("위치 응답: ${delayLabel(location.delayMillis, location.neverResponds)}")
        appendLine("개별 에러:")
        if (modes.isEmpty()) {
            append("  없음")
        } else {
            modes.forEach { (scenario, mode) -> appendLine("  ${scenario.title}: ${mode.status}") }
        }
    }

private fun delayLabel(
    delay: Long,
    never: Boolean,
): String =
    when {
        never -> "응답 없음"
        delay == 0L -> "없음"
        else -> "${delay}ms 추가 지연"
    }

private val LocationSimulationMode.label: String
    get() =
        when (this) {
            LocationSimulationMode.ACTUAL -> "실제 위치 사용"
            LocationSimulationMode.FIXED -> "지정 좌표 사용"
            LocationSimulationMode.PERMISSION_DENIED -> "권한 거부"
            LocationSimulationMode.SERVICES_DISABLED -> "위치 서비스 꺼짐"
            LocationSimulationMode.UNAVAILABLE -> "위치 확인 실패"
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
