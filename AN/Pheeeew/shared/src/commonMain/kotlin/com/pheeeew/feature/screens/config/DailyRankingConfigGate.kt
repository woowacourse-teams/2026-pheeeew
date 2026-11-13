package com.pheeeew.feature.screens.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.domain.model.config.AppConfigState

@Composable
fun DailyRankingConfigGate(
    state: AppConfigState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (state.initialized && state.config.dailyPressRankingEnabled) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.initialized) {
                CircularLoadingIndicator()
            } else {
                Text("일일 순위를 잠시 점검하고 있어요.")
                TextButton(onClick = onRetry, enabled = !state.refreshing) { Text("설정 다시 확인") }
            }
            TextButton(onClick = onBack) { Text("뒤로") }
        }
    }
}
