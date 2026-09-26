package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.legacy.core.designsystem.theme.AppTheme

internal const val SETTINGS_CONTACT_EMAIL = "contact@pheeeew.com"

@Composable
internal fun ContactCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(25.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 21.dp)
                .shadow(3.dp, shape, clip = false)
                .border(1.4.dp, SettingsColors.Ink, shape)
                .background(Color.White, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(
                        42.dp,
                    ).background(Color.White, CircleShape)
                    .border(1.dp, SettingsColors.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SettingsLineIcon(SettingsIcon.Mail, Modifier.size(25.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "문의하기",
                color = SettingsColors.Ink,
                style = AppTheme.typography.sectionHeader.copy(fontWeight = FontWeight.Bold),
            )
            Text(SETTINGS_CONTACT_EMAIL, color = SettingsColors.Ink, style = AppTheme.typography.caption)
        }
    }
}

@Preview
@Composable
private fun ContactCardPreview() {
    AppTheme {
        ContactCard(onClick = {}, modifier = Modifier.padding(20.dp))
    }
}
