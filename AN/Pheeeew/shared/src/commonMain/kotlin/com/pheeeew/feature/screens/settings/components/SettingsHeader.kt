package com.pheeeew.feature.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.BasicTopBar
import com.pheeeew.feature.screens.settings.SettingsTheme
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_title

@Composable
internal fun SettingsHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTopBar(
        title = stringResource(Res.string.settings_title),
        onBack = onBackClick,
        modifier = modifier,
        height = 64.dp,
        showBackIndication = false,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsHeaderPreview() {
    SettingsTheme {
        SettingsHeader(onBackClick = {})
    }
}
