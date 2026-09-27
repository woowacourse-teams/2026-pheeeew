package com.pheeeew.feature.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.legacy.core.designsystem.theme.AppTheme
import com.pheeeew.legacy.core.designsystem.theme.LocalAppTypography

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    AppTheme {
        val base = LocalAppTypography.current
        val notoSansKr = notoSansKrFontFamily()
        val typography =
            remember(base, notoSansKr) {
                base.copy(
                    screenTitle = base.screenTitle.copy(fontFamily = notoSansKr, fontSize = 24.sp),
                    sectionHeader = base.sectionHeader.copy(fontFamily = notoSansKr),
                    menuItem = base.menuItem.copy(fontFamily = notoSansKr, fontSize = 18.sp),
                    caption = base.caption.copy(fontFamily = notoSansKr, fontSize = 14.sp),
                    button = base.button.copy(fontFamily = notoSansKr),
                    dialogTitle = base.dialogTitle.copy(fontFamily = FontFamily.Default),
                    dialogBody = base.dialogBody.copy(fontFamily = FontFamily.Default),
                )
            }

        CompositionLocalProvider(LocalAppTypography provides typography, content = content)
    }
}
