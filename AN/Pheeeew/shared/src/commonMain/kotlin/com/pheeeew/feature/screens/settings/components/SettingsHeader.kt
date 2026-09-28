package com.pheeeew.feature.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.BasicTopBar
import com.pheeeew.feature.screens.settings.SettingsTheme

@Composable
internal fun SettingsHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTopBar(
        title = "설정",
        onBack = onBackClick,
        modifier = modifier,
        height = 64.dp,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsHeaderPreview() {
    SettingsTheme {
        SettingsHeader(onBackClick = {})
    }
}
