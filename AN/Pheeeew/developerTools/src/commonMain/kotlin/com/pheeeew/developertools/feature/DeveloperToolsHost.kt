package com.pheeeew.developertools.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pheeeew.core.navigation.PredictiveBackContent
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.developertools.data.simulation.DeveloperToolsMapStyleOverride
import com.pheeeew.developertools.data.simulation.NetworkRecovery
import com.pheeeew.feature.screens.map.renderer.LocalMapStyleOverrideProvider

/** Shared Debug navigation and apply flow; platform hosts own only their UI graph disposal. */
@Composable
fun DeveloperToolsHost(
    info: DeveloperToolsInfo,
    onClearUiSession: () -> Unit,
    onResetOnboarding: () -> Unit,
    configRepository: AppConfigRepository? = null,
    content: @Composable (onOpenDeveloperTools: () -> Unit) -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(DeveloperToolsPage.HOME) }
    var restartKey by rememberSaveable { mutableStateOf(0) }
    var pendingApply by remember { mutableStateOf<(() -> Unit)?>(null) }
    val applying = pendingApply
    val recovery by NetworkRecovery.controller.run.collectAsState()

    CompositionLocalProvider(LocalMapStyleOverrideProvider provides DeveloperToolsMapStyleOverride) {
        if (applying != null) {
            // Removing content disposes its UI-scoped resources before platform cleanup and settings apply.
            LaunchedEffect(applying) {
                onClearUiSession()
                applying()
                restartKey++
                pendingApply = null
            }
        } else {
            key(restartKey) {
                Box(Modifier.fillMaxSize()) {
                    content { visible = true }
                }
            }
        }
    }

    val onBack = {
        if (page != DeveloperToolsPage.HOME) page = DeveloperToolsPage.HOME else visible = false
    }
    if (visible) {
        PredictiveBackContent(onBack = onBack, content = {
            DeveloperToolsScreen(
                info = info,
                configRepository = configRepository,
                page = page,
                onOpenPage = { page = it },
                onBack = onBack,
                onResetOnboarding = onResetOnboarding,
                onRestart = { apply ->
                    NetworkRecovery.controller.stop()
                    page = DeveloperToolsPage.HOME
                    visible = false
                    pendingApply = apply
                },
                onStartRecovery = { restoreAfterMillis ->
                    if (NetworkRecovery.controller.start(restoreAfterMillis)) {
                        page = DeveloperToolsPage.HOME
                        visible = false
                    }
                },
            )
        })
    }
    if (!visible && recovery != null) {
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .safeDrawingPadding()
                        .padding(8.dp)
                        .widthIn(max = 180.dp),
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("복구 시나리오: 강제 오프라인")
                    Text(if (recovery?.restoreAfterMillis == null) "수동 복구 대기 중" else "자동 복구 대기 중")
                    TextButton(onClick = NetworkRecovery.controller::restore) { Text("지금 복구") }
                }
            }
        }
    }
}
