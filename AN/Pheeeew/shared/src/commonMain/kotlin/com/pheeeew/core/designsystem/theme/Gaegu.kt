package com.pheeeew.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.gaegu
import pheeeew.shared.generated.resources.gaegu_bold

@Composable
fun gaeguFontFamily(): FontFamily {
    val regular = Font(Res.font.gaegu, weight = FontWeight.Normal)
    val bold = Font(Res.font.gaegu_bold, weight = FontWeight.Bold)
    return remember(regular, bold) { FontFamily(regular, bold) }
}
