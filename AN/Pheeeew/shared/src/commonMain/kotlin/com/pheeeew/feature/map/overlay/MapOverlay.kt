package com.pheeeew.feature.map.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_my_location
import pheeeew.shared.generated.resources.ic_settings

@Composable
fun MapOverlay(
    onSettingsClick: () -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    errorMessage: String?,
    controlsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OverlayIconButton(
            icon = Res.drawable.ic_settings,
            contentDescription = "설정",
            onClick = onSettingsClick,
            enabled = controlsEnabled,
            modifier =
                Modifier
                    .align(Alignment.Start)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (errorMessage != null) {
            MapErrorBanner(
                message = errorMessage,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ZoomControl(onZoomInClick = onZoomInClick, onZoomOutClick = onZoomOutClick, enabled = controlsEnabled)
            OverlayIconButton(
                icon = Res.drawable.ic_my_location,
                contentDescription = "현재 위치로 이동",
                onClick = onMyLocationClick,
                enabled = controlsEnabled,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Preview
@Composable
private fun MapOverlayPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            MapOverlay(
                onSettingsClick = {},
                onZoomInClick = {},
                onZoomOutClick = {},
                onMyLocationClick = {},
                errorMessage = null,
                controlsEnabled = true,
            )
        }
    }
}

@Preview
@Composable
private fun MapOverlayNetworkErrorPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            MapOverlay(
                onSettingsClick = {},
                onZoomInClick = {},
                onZoomOutClick = {},
                onMyLocationClick = {},
                errorMessage = "인터넷 연결 상태를 확인해주세요!",
                controlsEnabled = true,
            )
        }
    }
}

@Preview
@Composable
private fun MapOverlayPermissionErrorPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            MapOverlay(
                onSettingsClick = {},
                onZoomInClick = {},
                onZoomOutClick = {},
                onMyLocationClick = {},
                errorMessage = "설정에서 위치 권한을 '허용'으로 변경해주세요.",
                controlsEnabled = true,
            )
        }
    }
}

@Preview
@Composable
private fun MapOverlayGpsErrorPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            MapOverlay(
                onSettingsClick = {},
                onZoomInClick = {},
                onZoomOutClick = {},
                onMyLocationClick = {},
                errorMessage = "GPS 수신이 원활하지 않습니다.",
                controlsEnabled = true,
            )
        }
    }
}
