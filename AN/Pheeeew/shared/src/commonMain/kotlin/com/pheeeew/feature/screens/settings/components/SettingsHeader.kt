package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_back

@Composable
internal fun SettingsHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(top = 36.dp).height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .size(46.dp)
                    .border(1.5.dp, SettingsColors.Ink, RoundedCornerShape(16.dp)),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = "뒤로가기",
                tint = SettingsColors.Ink,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = "설정",
            color = SettingsColors.Ink,
            style = AppTheme.typography.screenTitle,
        )
    }
}

@Preview
@Composable
private fun SettingsHeaderPreview() {
    AppTheme {
        SettingsHeader(onBackClick = {})
    }
}
