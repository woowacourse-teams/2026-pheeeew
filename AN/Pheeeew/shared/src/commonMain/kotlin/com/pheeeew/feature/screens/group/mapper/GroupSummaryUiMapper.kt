package com.pheeeew.feature.screens.group.mapper

import com.pheeeew.domain.model.group.Group
import com.pheeeew.domain.model.group.GroupPreview
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.toUiShape
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

fun Group.toSummaryUiModel(weeklyStampCount: Long? = null): GroupSummaryUiModel =
    GroupSummaryUiModel(
        id = GroupId(id.value),
        name = name,
        memberCount = memberCount,
        weeklyStampCount = weeklyStampCount,
        stamp = stamp.toAppearanceUiModel(),
        description = description,
    )

fun GroupPreview.toSummaryUiModel(): GroupSummaryUiModel =
    GroupSummaryUiModel(
        id = GroupId(id.value),
        name = name,
        memberCount = memberCount,
        weeklyStampCount = null,
        stamp = stamp.toAppearanceUiModel(),
        description = description,
    )

private fun GroupStamp.toAppearanceUiModel() =
    StampAppearanceUiModel(
        label = text,
        shape = frame.toUiShape(),
        fillArgb = backgroundColor.argb,
        textArgb = textColor.argb,
    )
