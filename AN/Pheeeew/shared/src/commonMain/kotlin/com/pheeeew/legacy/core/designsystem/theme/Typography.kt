package com.pheeeew.legacy.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AppTypography(
    // Gaegu — casual brand voice: screens, navigation, lists
    val screenTitle: TextStyle,
    val sectionHeader: TextStyle,
    val menuItem: TextStyle,
    val caption: TextStyle,
    // System default — formal/trust-critical: dialogs and alerts
    val dialogTitle: TextStyle,
    val dialogBody: TextStyle,
    val button: TextStyle,
)

val LocalAppTypography =
    staticCompositionLocalOf {
        AppTypography(
            screenTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            sectionHeader = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal),
            menuItem = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            caption = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Light),
            dialogTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            dialogBody = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
            button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
    }
