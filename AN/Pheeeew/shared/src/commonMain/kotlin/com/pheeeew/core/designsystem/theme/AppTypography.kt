package com.pheeeew.core.designsystem.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Partial role styles that retain the surrounding Material text style. */
object AppTypography {
    @Composable
    fun basicTopBarTitle(): TextStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)

    @Composable
    fun settingsSectionTitle(): TextStyle =
        LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)

    @Composable
    fun settingsActionTitle(): TextStyle =
        LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    @Composable
    fun settingsActionSecondary(): TextStyle = LocalTextStyle.current.copy(fontSize = 12.sp, lineHeight = 16.sp)

    @Composable
    fun confirmDialogTitle(): TextStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)

    @Composable
    fun confirmDialogBody(): TextStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 22.sp)

    @Composable
    fun reportSubmitLabel(): TextStyle = LocalTextStyle.current.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
}
