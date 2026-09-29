package com.pheeeew.feature.screens.map.record

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.pheeeew.feature.screens.map.overlay.MapErrorBanner
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
        isOffline && showOffline -> "인터넷 연결이 끊겼어요. 연결 상태를 확인해 주세요."
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
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density) + with(density) { 76.dp.roundToPx() }
    val position =
        remember(top) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset((windowSize.width - popupContentSize.width) / 2, top)
            }
        }
    Popup(
        popupPositionProvider = position,
        properties = PopupProperties(focusable = false),
    ) {
        MapErrorBanner(
            message = message,
            modifier =
                modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        )
    }
}
