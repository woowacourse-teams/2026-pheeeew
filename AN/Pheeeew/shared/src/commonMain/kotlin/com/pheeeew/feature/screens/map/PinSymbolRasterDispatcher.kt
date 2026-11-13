package com.pheeeew.feature.screens.map

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher

internal expect fun pinSymbolRasterDispatcher(): CoroutineDispatcher

internal expect fun ImageBitmap.toEmotionPinSymbolImage(
    key: String,
    densityDpi: Int,
): EmotionPinSymbolImage
