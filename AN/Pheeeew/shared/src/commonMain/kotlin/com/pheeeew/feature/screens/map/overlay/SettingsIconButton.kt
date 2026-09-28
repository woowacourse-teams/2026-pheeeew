package com.pheeeew.feature.screens.map.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_settings_outline

@Composable
internal fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(46.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_settings_outline),
            contentDescription = "설정 버튼",
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.5.dp))
                    .clickable(role = Role.Button, onClick = onClick),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsIconButtonPreview() {
    SettingsIconButton(onClick = {})
}
