package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.settings.SettingsTheme

@Composable
internal fun SettingsActionRow(
    title: String,
    icon: SettingsIcon,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .let { row ->
                    if (onClick == null) {
                        row
                    } else {
                        row.clickable(interactionSource = null, indication = null, onClick = onClick)
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBadge(icon = icon, highlighted = highlighted)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = SettingsColors.Ink,
            fontFamily = notoSansKrFontFamily(),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = SettingsColors.Secondary,
                fontFamily = notoSansKrFontFamily(),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        } else if (onClick != null) {
            SettingsChevron()
        }
    }
}

@Preview
@Composable
private fun SettingsActionRowPreview() {
    SettingsTheme {
        androidx.compose.foundation.layout.Column {
            SettingsActionRow("접근 권한 설정", SettingsIcon.Tune, highlighted = true, onClick = {})
            SettingsDivider()
            SettingsActionRow("앱 버전", SettingsIcon.Info, trailingText = "1.1.1")
        }
    }
}
