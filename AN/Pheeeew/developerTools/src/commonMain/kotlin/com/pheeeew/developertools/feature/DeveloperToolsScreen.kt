package com.pheeeew.developertools.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.data.local.config.AppConfigStore
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.developertools.data.simulation.ErrorSimulation
import com.pheeeew.domain.model.config.AppConfigSnapshot
import kotlinx.coroutines.launch

internal enum class DeveloperToolsPage { HOME, ERROR_SIMULATION, LOCAL_DATA, REMOTE_CONFIG }

@Composable
internal fun DeveloperToolsScreen(
    info: DeveloperToolsInfo,
    page: DeveloperToolsPage,
    onOpenPage: (DeveloperToolsPage) -> Unit,
    onBack: () -> Unit,
    onResetOnboarding: () -> Unit,
    onRestart: (() -> Unit) -> Unit,
    onStartRecovery: (Long?) -> Unit,
    configRepository: AppConfigRepository? = null,
) {
    val scope = rememberCoroutineScope()
    var applyingLocalData by remember { mutableStateOf(false) }
    var localDataError by remember { mutableStateOf<String?>(null) }
    Surface(modifier = Modifier.fillMaxSize()) {
        if (page == DeveloperToolsPage.ERROR_SIMULATION) {
            LaunchedEffect(Unit) { ErrorSimulation.enableDeveloperTools() }
        }
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(if (page != DeveloperToolsPage.HOME) "‹ 개발자 도구" else "뒤로")
            }
            Spacer(Modifier.height(16.dp))
            when (page) {
                DeveloperToolsPage.HOME -> {
                    DeveloperToolsHome(
                        info = info,
                        onOpenErrorScenarios = { onOpenPage(DeveloperToolsPage.ERROR_SIMULATION) },
                        onOpenLocalData = { onOpenPage(DeveloperToolsPage.LOCAL_DATA) },
                        onOpenRemoteConfig = { onOpenPage(DeveloperToolsPage.REMOTE_CONFIG) },
                    )
                }

                DeveloperToolsPage.ERROR_SIMULATION -> {
                    ErrorSimulationScreen(onRestart = onRestart, onStartRecovery = onStartRecovery)
                }

                DeveloperToolsPage.LOCAL_DATA -> {
                    LocalDataSettingsScreen(
                        canClearConfig = configRepository != null,
                        applying = applyingLocalData,
                        error = localDataError,
                        onApply = { onboarding, config ->
                            applyingLocalData = true
                            localDataError = null
                            scope.launch {
                                if (config) configRepository?.clearCache()
                                if (config && configRepository?.state?.value?.error == "설정 캐시 초기화 실패") {
                                    localDataError = "원격 설정 캐시를 초기화하지 못했습니다. 다시 시도해 주세요."
                                    applyingLocalData = false
                                } else if (onboarding) {
                                    onRestart(onResetOnboarding)
                                } else {
                                    applyingLocalData = false
                                    onBack()
                                }
                            }
                        },
                    )
                }

                DeveloperToolsPage.REMOTE_CONFIG -> {
                    if (configRepository != null) {
                        RemoteConfigTools(configRepository, onRestart)
                    } else {
                        Text("원격 설정을 사용할 수 없습니다.")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun DeveloperToolsScreenPreview() {
    val scope = rememberCoroutineScope()
    val repository =
        remember(scope) {
            AppConfigRepository(
                store =
                    object : AppConfigStore {
                        override suspend fun read(): String? = null

                        override suspend fun clear() = Unit

                        override suspend fun write(value: String) = Unit
                    },
                scope = scope,
                load = {
                    AppConfigSnapshot(
                        revision = 1,
                        dailyPressRankingEnabled = true,
                        noticeMessage = "공지 미리보기",
                        updatedAt = "2026-10-29T03:00:00Z",
                    )
                },
            )
        }
    DeveloperToolsScreen(
        info = DeveloperToolsInfo("Android", "2.5.0", "13", "Debug", "개발", "https://example.com"),
        page = DeveloperToolsPage.HOME,
        onOpenPage = {},
        onBack = {},
        onResetOnboarding = {},
        onRestart = {},
        onStartRecovery = {},
        configRepository = repository,
    )
}
