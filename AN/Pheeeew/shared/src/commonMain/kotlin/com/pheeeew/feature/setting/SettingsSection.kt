package com.pheeeew.feature.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = AppTheme.typography.sectionHeader,
            color = AppTheme.colors.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider(color = AppColors.Navy800, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Preview
@Composable
private fun SettingsSectionPreview() {
    AppTheme {
        Column(modifier = Modifier.background(AppTheme.colors.background)) {
            SettingsSection(
                title = "앱 설정",
                content = {
                    SettingsMenuItem(title = "테마 설정", onClick = {})
                    SettingsMenuItem(title = "위치 권한 설정", onClick = {})
                },
            )
        }
    }
}
