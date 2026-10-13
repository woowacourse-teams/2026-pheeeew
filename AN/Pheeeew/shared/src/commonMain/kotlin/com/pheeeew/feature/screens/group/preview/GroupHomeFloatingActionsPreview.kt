package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.home.component.GroupHomeFloatingActions

@Preview(name = "그룹 플로팅 메뉴 - 펼침", widthDp = 360, heightDp = 800)
@Preview(name = "그룹 플로팅 메뉴 - 좁은 화면", widthDp = 320, heightDp = 640)
@Composable
private fun GroupHomeFloatingActionsExpandedPreview() {
    AppTheme {
        Box(Modifier.fillMaxSize().background(AppColors.GroupBackground)) {
            GroupHomeFloatingActions({}, {}, Modifier.fillMaxSize(), initiallyExpanded = true)
        }
    }
}
