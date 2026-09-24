package com.pheeeew.feature.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_menu
import pheeeew.shared.generated.resources.ic_my_location
import pheeeew.shared.generated.resources.ic_settings

@Composable
fun MapOverlay(
    onListClick: () -> Unit,
    onSettingClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    isRequestingLocation: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize()
            .statusBarsPadding()
            .padding( 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(AppColors.Surface)
                    .border(width = 1.dp, color = AppColors.Border, shape = RoundedCornerShape(100.dp))
                    .clickable(onClick = onListClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_menu),
                    contentDescription = "목록 열기",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "주변 목록",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AppColors.Surface)
                    .border(width = 1.dp, color = AppColors.Border, shape = RoundedCornerShape(15.dp))
                    .clickable(onClick = onSettingClick)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_settings),
                    contentDescription = "설정 버튼",
                    modifier = Modifier.size(24.dp),
                )
            }

        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .clip(CircleShape)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .background(AppColors.Surface)
                .clickable(
                    enabled = !isRequestingLocation,
                    onClick = onMyLocationClick,
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_my_location),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color(0xff2670F8),
            )
        }
    }
}

@Preview
@Composable
private fun MapOverlayPreview() {
    MapOverlayPreviewContent(isRequestingLocation = false)
}

@Preview
@Composable
private fun MapOverlayRequestingLocationPreview() {
    MapOverlayPreviewContent(isRequestingLocation = true)
}

@Composable
private fun MapOverlayPreviewContent(isRequestingLocation: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECEAE5)),
    ) {
        MapOverlay(
            onListClick = {},
            onSettingClick = {},
            onMyLocationClick = {},
            isRequestingLocation = isRequestingLocation,
        )
    }
}
