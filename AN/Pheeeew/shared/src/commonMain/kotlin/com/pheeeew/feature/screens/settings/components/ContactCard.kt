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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.appSurface
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.settings.SettingsTheme

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
                .padding(horizontal = AppSpacing.xl.copy(value = 21.dp).dp)
                .shadow(3.dp, shape, clip = false)
                .border(AppBorders.Standard, MaterialTheme.appColors.settings.iconInk, shape)
                .background(MaterialTheme.colorScheme.appSurface, shape)
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .padding(
                    horizontal = AppSpacing.lg.copy(value = 18.dp).dp,
                    vertical = AppSpacing.xxl.copy(value = 28.dp).dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(
                        42.dp,
                    ).background(MaterialTheme.colorScheme.appSurface, CircleShape)
                    .border(AppBorders.Standard, MaterialTheme.appColors.settings.iconInk, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SettingsLineIcon(SettingsIcon.Mail, Modifier.size(25.dp))
        }
        Spacer(Modifier.width(AppSpacing.md.copy(value = 14.dp).dp))
        Column {
            Text(
                "문의하기",
                color = MaterialTheme.appColors.settings.iconInk,
                fontFamily = notoSansKrFontFamily(),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = SETTINGS_CONTACT_EMAIL,
                color = MaterialTheme.appColors.settings.iconInk,
                fontFamily = notoSansKrFontFamily(),
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Preview
@Composable
private fun ContactCardPreview() {
    SettingsTheme {
        ContactCard(onClick = {}, modifier = Modifier.padding(AppSpacing.xl.dp))
    }
}
