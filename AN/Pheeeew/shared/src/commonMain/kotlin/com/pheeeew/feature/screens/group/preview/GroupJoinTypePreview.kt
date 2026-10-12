package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.create.GroupJoinTypeSelector
import com.pheeeew.feature.screens.group.model.GroupJoinType

@Preview(name = "가입 방식 선택 · 비공개", widthDp = 360, heightDp = 160, showBackground = true)
@Composable
private fun GroupJoinTypePrivatePreview() {
    GroupJoinTypePreviewFrame(selected = GroupJoinType.PRIVATE)
}

@Preview(name = "가입 방식 선택 · 공개", widthDp = 360, heightDp = 160, showBackground = true)
@Composable
private fun GroupJoinTypePublicPreview() {
    GroupJoinTypePreviewFrame(selected = GroupJoinType.PUBLIC)
}

@Composable
private fun GroupJoinTypePreviewFrame(selected: GroupJoinType) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.GroupBackground)
                .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        GroupJoinTypeSelector(
            selected = selected,
            enabled = true,
            onSelected = {},
        )
    }
}
