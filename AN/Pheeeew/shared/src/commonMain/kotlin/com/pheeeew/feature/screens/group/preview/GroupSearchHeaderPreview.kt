package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.home.GroupHomeTab
import com.pheeeew.feature.screens.group.home.component.GroupCatalogCard
import com.pheeeew.feature.screens.group.home.component.GroupHomeSwipePager
import com.pheeeew.feature.screens.group.home.component.GroupSearchHeader
import com.pheeeew.feature.screens.group.home.explore.GroupExploreContent

@Preview(name = "상단바 검색 · 닫힘·돋보기", widthDp = 360, heightDp = 520)
@Composable
private fun SearchClosedPreview() {
    SearchHeaderFrame(false, "", emptyList())
}

@Preview(name = "상단바 검색 · 열림·기록 없음", widthDp = 360, heightDp = 520)
@Composable
private fun SearchEmptyPreview() {
    SearchHeaderFrame(true, "", emptyList())
}

@Preview(name = "상단바 검색 · 열림·최근 검색", widthDp = 360, heightDp = 520)
@Composable
private fun SearchRecentPreview() {
    SearchHeaderFrame(true, "", listOf("산책", "우테코", "우리 동네"))
}

@Preview(name = "상단바 검색 · 입력·일치 강조", widthDp = 360, heightDp = 520)
@Preview(name = "상단바 검색 · 입력·좁은 화면", widthDp = 320, heightDp = 520)
@Composable
private fun SearchQueryPreview() {
    SearchHeaderFrame(true, "산책", emptyList())
}

@Preview(name = "상단바 검색 · 닫은 후 결과 유지", widthDp = 360, heightDp = 520)
@Composable
private fun SearchSummaryPreview() {
    SearchHeaderFrame(false, "산책", listOf("산책"))
}

@Composable
private fun SearchHeaderFrame(
    openInitially: Boolean,
    initialQuery: String,
    initialHistory: List<String>,
    initialTab: GroupHomeTab = GroupHomeTab.Explore,
) {
    var open by remember { mutableStateOf(openInitially) }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var myQuery by remember { mutableStateOf(if (initialTab == GroupHomeTab.MyGroups) initialQuery else "") }
    var allQuery by remember { mutableStateOf(if (initialTab == GroupHomeTab.Explore) initialQuery else "") }
    var myHistory by remember {
        mutableStateOf(
            if (initialTab ==
                GroupHomeTab.MyGroups
            ) {
                initialHistory
            } else {
                emptyList()
            },
        )
    }
    var allHistory by remember {
        mutableStateOf(
            if (initialTab ==
                GroupHomeTab.Explore
            ) {
                initialHistory
            } else {
                emptyList()
            },
        )
    }
    val query = if (selectedTab == GroupHomeTab.MyGroups) myQuery else allQuery
    val history = if (selectedTab == GroupHomeTab.MyGroups) myHistory else allHistory
    AppTheme {
        GroupHomeSwipePager(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.fillMaxSize().background(AppColors.GroupBackground),
            isSearchOpen = open,
            headerContent = { tabs ->
                GroupSearchHeader(
                    isOpen = open,
                    query = query,
                    recentQueries = history,
                    showSummary = true,
                    searchTab = selectedTab,
                    onOpen = {
                        open = true
                    },
                    onClose = { open = false },
                    onQueryChanged = { if (selectedTab == GroupHomeTab.MyGroups) myQuery = it else allQuery = it },
                    onSubmit = {
                        if (query.isNotBlank()) {
                            if (selectedTab ==
                                GroupHomeTab.MyGroups
                            ) {
                                myHistory = listOf(query)
                            } else {
                                allHistory = listOf(query)
                            }
                        }
                        open = false
                    },
                    tabsContent = tabs,
                )
            },
        ) { tab ->
            Box(Modifier.padding(24.dp)) {
                if (tab == GroupHomeTab.Explore) {
                    val item = (HomeFixtures.exploreState.content as GroupExploreContent.Ready).items.first()
                    GroupCatalogCard(item.group, item.joinType, item.isJoined, {}, highlightQuery = query)
                }
            }
        }
    }
}

@Preview(name = "내 그룹 검색 · 안내와 최근 검색", widthDp = 360, heightDp = 520)
@Composable
private fun MyGroupRecentSearchPreview() {
    SearchHeaderFrame(true, "", listOf("우테코", "월요일"), GroupHomeTab.MyGroups)
}
