package com.pheeeew.legacy.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val gaegu = gaeguFontFamily()
    val base = LocalAppTypography.current
    val typography =
        remember(base, gaegu) {
            base.copy(
                screenTitle = base.screenTitle.copy(fontFamily = gaegu),
                sectionHeader = base.sectionHeader.copy(fontFamily = gaegu),
                menuItem = base.menuItem.copy(fontFamily = gaegu),
                caption = base.caption.copy(fontFamily = gaegu),
                dialogTitle = base.dialogTitle.copy(fontFamily = FontFamily.Default),
                dialogBody = base.dialogBody.copy(fontFamily = FontFamily.Default),
                button = base.button.copy(fontFamily = gaegu),
            )
        }

    CompositionLocalProvider(
        LocalAppTypography provides typography,
        LocalAppColorScheme provides LocalAppColorScheme.current,
    ) {
        content()
    }
}

object AppTheme {
    val typography: AppTypography
        @Composable get() = LocalAppTypography.current
    val colors: AppColorScheme
        @Composable get() = LocalAppColorScheme.current
}
