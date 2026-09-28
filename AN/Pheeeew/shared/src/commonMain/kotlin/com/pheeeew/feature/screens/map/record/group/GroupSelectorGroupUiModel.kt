package com.pheeeew.feature.screens.map.record.group

import com.pheeeew.domain.model.group.GroupStampItem
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.toUiShape

data class GroupSelectorGroupUiModel(
    val id: String,
    val name: String,
    val stamp: StampAppearanceUiModel?,
)

fun GroupStampItem.toSelectorUiModel(): GroupSelectorGroupUiModel =
    GroupSelectorGroupUiModel(
        id = id.value,
        name = name,
        stamp =
            StampAppearanceUiModel(
                label = stamp.text,
                shape = stamp.frame.toUiShape(),
                fillArgb = stamp.backgroundColor.argb,
                textArgb = stamp.textColor.argb,
            ),
    )
