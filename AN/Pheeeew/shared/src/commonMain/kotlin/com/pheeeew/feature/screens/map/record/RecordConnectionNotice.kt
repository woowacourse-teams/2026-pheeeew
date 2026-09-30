package com.pheeeew.feature.screens.map.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
internal fun rememberRecordConnectionMessage(
    active: Boolean,
    isOffline: Boolean,
): String? {
    var sawOffline by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var showOffline by remember { mutableStateOf(false) }
    LaunchedEffect(active, isOffline) {
        showRecovery = false
        showOffline = false
        if (!active) {
            sawOffline = false
        } else if (isOffline) {
            sawOffline = true
            showOffline = true
            delay(4_000)
            showOffline = false
        } else if (sawOffline) {
            sawOffline = false
            showRecovery = true
            delay(4_000)
            showRecovery = false
        }
    }
    return when {
        !active -> null
        isOffline && showOffline -> "인터넷 연결이 끊겼어요. 연결 상태를 확인해주세요."
        !isOffline && showRecovery -> "인터넷이 다시 연결됐어요. 등록되지 않았다면 다시 시도해 주세요."
        else -> null
    }
}
