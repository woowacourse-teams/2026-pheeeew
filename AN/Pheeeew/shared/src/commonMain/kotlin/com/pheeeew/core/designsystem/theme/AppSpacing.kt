package com.pheeeew.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Named spacing values shared by screens and components. */
data class SpacingToken(
    val value: Dp,
) {
    val dp: Dp
        get() = value
}

object AppSpacing {
    val xs = SpacingToken(4.dp)
    val sm = SpacingToken(8.dp)
    val md = SpacingToken(12.dp)
    val lg = SpacingToken(16.dp)
    val xl = SpacingToken(20.dp)
    val xxl = SpacingToken(24.dp)
    val xxxl = SpacingToken(32.dp)

    // Semantic aliases keep common layout intent visible without changing scale values.
    val screen = xxl.copy(value = 24.dp)
    val card = lg.copy(value = 16.dp)
    val chip = sm.copy(value = 8.dp)
    val cardGap = md.copy(value = 12.dp)
    val sectionGap = xl.copy(value = 20.dp)
}
