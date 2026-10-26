package com.pheeeew.feature.screens.collection.card

import com.pheeeew.feature.emotion.model.EmotionType

data class CollectionCardUiModel(
    val id: String,
    val period: String,
    val title: String,
    val emotion: EmotionType,
    val pressLabel: String,
    val pressCount: Long,
    val stampLabel: String,
    val stampCount: Long,
    val characterImageUrl: String? = null,
    val textStampImageUrl: String? = null,
    val roundStampImageUrl: String? = null,
)
