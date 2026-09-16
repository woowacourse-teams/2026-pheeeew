package com.pheeeew.feature.map.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_error

@Composable
fun MapErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20),
        color = AppTheme.colors.surface,
        contentColor = AppTheme.colors.onBackground,
        border = BorderStroke(1.dp, AppTheme.colors.danger),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_error),
                contentDescription = null,
                tint = AppTheme.colors.danger,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = AppTheme.typography.menuItem,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun MapErrorBannerNetworkPreview() {
    AppTheme {
        Box(
            modifier =
                Modifier
                    .background(AppTheme.colors.background)
                    .padding(24.dp),
        ) {
            MapErrorBanner(
                message = "인터넷 연결을 확인한 후 다시 시도해 주세요.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun MapErrorBannerRendererPreview() {
    AppTheme {
        Box(
            modifier =
                Modifier
                    .background(AppTheme.colors.background)
                    .padding(24.dp),
        ) {
            MapErrorBanner(
                message = "지도를 불러오지 못했어요. 잠시 후 재시도해 주세요.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun MapErrorBannerWithActionsPreview() {
    AppTheme {
        Box(
            modifier =
                Modifier
                    .background(AppTheme.colors.background)
                    .padding(24.dp),
        ) {
            MapErrorBanner(
                message = "위치 정보를 불러오지 못했어요.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
