package com.pheeeew.legacy.feature.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_back

@Composable
fun SettingsTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = "뒤로가기",
                tint = AppTheme.colors.onBackground,
            )
        }
    }
}

@Preview
@Composable
private fun SettingsTopBarPreview() {
    AppTheme {
        Box(modifier = Modifier.background(AppTheme.colors.background)) {
            SettingsTopBar(onBackClick = {})
        }
    }
}
