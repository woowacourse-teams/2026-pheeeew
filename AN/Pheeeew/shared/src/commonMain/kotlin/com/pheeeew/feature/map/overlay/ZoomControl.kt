package com.pheeeew.feature.map.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_zoom_in
import pheeeew.shared.generated.resources.ic_zoom_out

@Composable
fun ZoomControl(
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = AppColors.MapControlBackground,
        contentColor = AppColors.MapControlContent,
        border = BorderStroke(1.dp, AppColors.MapControlBorder),
        shadowElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier.size(44.dp).clickable(enabled = enabled, onClick = onZoomInClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(Res.drawable.ic_zoom_in), contentDescription = "확대")
            }
            HorizontalDivider(color = AppColors.MapControlBorder)
            Box(
                modifier = Modifier.size(44.dp).clickable(enabled = enabled, onClick = onZoomOutClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(Res.drawable.ic_zoom_out), contentDescription = "축소")
            }
        }
    }
}

@Preview
@Composable
private fun ZoomControlPreview() {
    AppTheme {
        Box(modifier = Modifier.background(AppTheme.colors.background).padding(24.dp)) {
            ZoomControl(onZoomInClick = {}, onZoomOutClick = {}, enabled = true)
        }
    }
}
