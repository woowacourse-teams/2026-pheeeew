package com.pheeeew.feature.emotion.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

/** Avoids extra draw dependencies when the debug probe is absent. */
internal fun Modifier.pressProbeDraw(
    measurement: ((String) -> Unit)?,
    draw: ContentDrawScope.() -> Unit,
): Modifier = if (measurement == null) this else drawWithContent(draw)
