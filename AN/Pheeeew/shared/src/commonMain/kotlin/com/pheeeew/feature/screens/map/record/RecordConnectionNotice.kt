package com.pheeeew.feature.screens.map.record

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import kotlinx.coroutines.delay

@Composable
internal fun rememberRecordConnectionMessage(
    active: Boolean,
    isOffline: Boolean,
): String? {
    var sawOffline by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    LaunchedEffect(active, isOffline) {
        showRecovery = false
        if (!active) {
            sawOffline = false
        } else if (isOffline) {
            sawOffline = true
        } else if (sawOffline) {
            sawOffline = false
            showRecovery = true
            delay(4_000)
            showRecovery = false
        }
    }
    return when {
        !active -> null
        isOffline -> "인터넷 연결이 끊겼어요. 작성 중인 내용은 유지돼요. 연결 후 등록해 주세요."
        showRecovery -> "인터넷이 다시 연결됐어요. 등록되지 않았다면 다시 시도해 주세요."
        else -> null
    }
}

@Composable
internal fun RecordConnectionNotice(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = AppColors.Background,
        contentColor = AppColors.TextPrimary,
    ) {
        Text(message, Modifier.padding(12.dp), fontSize = 13.sp, lineHeight = 19.sp)
    }
}
