package com.pheeeew.feature.screens.collection

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

internal const val COLLECTION_CARD_SHARE_URL = "https://pheeeew.com"

internal enum class CollectionImageAction { SAVE, SHARE }

internal enum class CollectionImageActionOutcome { SUCCEEDED, CANCELLED, CAPTURE_FAILED, FAILED }

internal data class CollectionImageActionResult(
    val cardId: String,
    val action: CollectionImageAction,
    val outcome: CollectionImageActionOutcome,
)

internal data class CollectionCardImageActions(
    val save: suspend (ByteArray) -> CollectionImageActionOutcome,
    val share: suspend (bytes: ByteArray, url: String) -> CollectionImageActionOutcome,
)

@Composable
internal expect fun rememberCollectionCardImageActions(): CollectionCardImageActions

internal expect fun ImageBitmap.toPngBytes(): ByteArray
