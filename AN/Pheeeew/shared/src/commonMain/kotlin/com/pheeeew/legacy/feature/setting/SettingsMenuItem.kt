package com.pheeeew.legacy.feature.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.theme.AppTheme

@Composable
fun SettingsMenuItem(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = AppTheme.typography.menuItem, color = AppTheme.colors.onBackground)
        if (trailingText != null) {
            Text(text = trailingText, style = AppTheme.typography.caption, color = AppTheme.colors.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun SettingsMenuItemPreview() {
    AppTheme {
        SettingsMenuItem(
            title = "앱 버전",
            trailingText = "1.0.0",
            modifier = Modifier.background(AppTheme.colors.background),
        )
    }
}
