package com.pheeeew.feature.screens.map.record.group

import com.pheeeew.feature.component.stamp.StampAppearanceUiModel

data class GroupSelectorGroupUiModel(
    val id: String,
    val name: String,
    val stampLabel: String,
    val appearance: StampAppearanceUiModel? = null,
    val showStamp: Boolean = true,
)
