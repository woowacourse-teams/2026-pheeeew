package com.pheeeew.feature.screens.map.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.appBorder
import com.pheeeew.core.designsystem.theme.appSurface
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_settings
import pheeeew.shared.generated.resources.map_settings

@Composable
internal fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(AppShapes.Button)
                .background(MaterialTheme.colorScheme.appSurface)
                .border(
                    width = AppBorders.Standard,
                    color = MaterialTheme.colorScheme.appBorder,
                    shape = AppShapes.Button,
                ).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_settings),
            contentDescription = stringResource(Res.string.map_settings),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Preview
@Composable
private fun SettingsIconButtonPreview() {
    SettingsIconButton(onClick = {})
}
