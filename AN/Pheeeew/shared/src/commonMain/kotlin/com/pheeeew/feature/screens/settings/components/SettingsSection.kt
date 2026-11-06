package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.AppTypography
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.settings.SettingsTheme

@Composable
internal fun SettingsSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = SettingsColors.Ink,
        fontFamily = notoSansKrFontFamily(),
        style = AppTypography.settingsSectionTitle(),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppSpacing.xxl.copy(value = 26.dp).dp,
                ).padding(bottom = AppSpacing.xs.copy(value = 5.dp).dp),
    )
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = AppShapes.SectionCard
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xl.copy(value = 21.dp).dp)
                .shadow(3.dp, shape, clip = false)
                .border(AppBorders.Standard, SettingsColors.Ink, shape)
                .background(AppColors.Background, shape)
                .padding(horizontal = AppSpacing.lg.copy(value = 18.dp).dp),
    ) {
        content()
    }
}

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = SettingsColors.Divider, thickness = AppBorders.Thin)
}

@Preview
@Composable
private fun SettingsSectionTitlePreview() {
    SettingsTheme {
        Column(Modifier.background(AppColors.Background)) {
            SettingsSectionTitle("이용 안내")
        }
    }
}

@Preview
@Composable
private fun SettingsCardPreview() {
    SettingsTheme {
        SettingsCard {
            SettingsActionRow("접근 권한 설정", SettingsIcon.Tune, highlighted = true, onClick = {})
        }
    }
}

@Preview
@Composable
private fun SettingsDividerPreview() {
    SettingsTheme {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xl.dp)) {
            SettingsDivider()
        }
    }
}
