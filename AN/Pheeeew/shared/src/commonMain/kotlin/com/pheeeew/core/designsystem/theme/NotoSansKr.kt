package com.pheeeew.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.tap_noto_100
import pheeeew.shared.generated.resources.tap_noto_200
import pheeeew.shared.generated.resources.tap_noto_300
import pheeeew.shared.generated.resources.tap_noto_400
import pheeeew.shared.generated.resources.tap_noto_500
import pheeeew.shared.generated.resources.tap_noto_600
import pheeeew.shared.generated.resources.tap_noto_700
import pheeeew.shared.generated.resources.tap_noto_800
import pheeeew.shared.generated.resources.tap_noto_900

@Composable
fun notoSansKrFontFamily(): FontFamily {
    val thin = Font(Res.font.tap_noto_100, weight = FontWeight.Thin)
    val extraLight = Font(Res.font.tap_noto_200, weight = FontWeight.ExtraLight)
    val light = Font(Res.font.tap_noto_300, weight = FontWeight.Light)
    val regular = Font(Res.font.tap_noto_400, weight = FontWeight.Normal)
    val medium = Font(Res.font.tap_noto_500, weight = FontWeight.Medium)
    val semiBold = Font(Res.font.tap_noto_600, weight = FontWeight.SemiBold)
    val bold = Font(Res.font.tap_noto_700, weight = FontWeight.Bold)
    val extraBold = Font(Res.font.tap_noto_800, weight = FontWeight.ExtraBold)
    val black = Font(Res.font.tap_noto_900, weight = FontWeight.Black)
    return remember(thin, extraLight, light, regular, medium, semiBold, bold, extraBold, black) {
        FontFamily(thin, extraLight, light, regular, medium, semiBold, bold, extraBold, black)
    }
}
