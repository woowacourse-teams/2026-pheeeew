package com.pheeeew.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.tap_noto_700
import pheeeew.shared.generated.resources.tap_noto_900

@Composable
fun notoSansKrFontFamily(): FontFamily {
    val bold = Font(Res.font.tap_noto_700, weight = FontWeight.Bold)
    val black = Font(Res.font.tap_noto_900, weight = FontWeight.Black)
    return remember(bold, black) { FontFamily(bold, black) }
}
