package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.legacy.core.designsystem.theme.AppTheme

@Composable
internal fun SettingsSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = SettingsColors.Ink,
        style = AppTheme.typography.sectionHeader.copy(fontWeight = FontWeight.Bold),
        modifier = modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(bottom = 5.dp),
    )
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 21.dp)
                .shadow(3.dp, shape, clip = false)
                .border(1.4.dp, SettingsColors.Ink, shape)
                .background(AppColors.Background, shape)
                .padding(horizontal = 18.dp),
    ) {
        content()
    }
}

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = SettingsColors.Divider, thickness = 1.dp)
}

@Preview
@Composable
private fun SettingsSectionTitlePreview() {
    AppTheme {
        Column(Modifier.background(AppColors.Background)) {
            SettingsSectionTitle("이용 안내")
        }
    }
}

@Preview
@Composable
private fun SettingsCardPreview() {
    AppTheme {
        SettingsCard {
            SettingsActionRow("접근 권한 설정", SettingsIcon.Tune, highlighted = true, onClick = {})
        }
    }
}

@Preview
@Composable
private fun SettingsDividerPreview() {
    AppTheme {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SettingsDivider()
        }
    }
}
