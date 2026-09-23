package com.pheeeew.legacy.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.gaegu_bold
import pheeeew.shared.generated.resources.gaegu_light
import pheeeew.shared.generated.resources.gaegu_regular

@Composable
fun gaeguFontFamily(): FontFamily {
    val light = Font(Res.font.gaegu_light, weight = FontWeight.Light)
    val regular = Font(Res.font.gaegu_regular, weight = FontWeight.Normal)
    val bold = Font(Res.font.gaegu_bold, weight = FontWeight.Bold)
    return remember(light, regular, bold) { FontFamily(light, regular, bold) }
}
