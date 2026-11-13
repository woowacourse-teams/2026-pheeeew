package com.pheeeew.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.bagel_fat_one

@Composable
fun bagelFatOneFontFamily(): FontFamily {
    val regular = Font(Res.font.bagel_fat_one, weight = FontWeight.Normal)
    return remember(regular) { FontFamily(regular) }
}
