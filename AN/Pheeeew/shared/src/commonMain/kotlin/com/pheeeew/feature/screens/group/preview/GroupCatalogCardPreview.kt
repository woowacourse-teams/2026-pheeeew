package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.home.component.GroupCatalogCard
import com.pheeeew.feature.screens.group.home.component.GroupListItem
import com.pheeeew.feature.screens.group.home.explore.GroupExploreContent
import com.pheeeew.feature.screens.group.model.GroupJoinType

@Preview(name = "전체 그룹 카드 · 참여 체크", widthDp = 360, heightDp = 640)
@Preview(name = "그룹 카드 · 좁은 화면", widthDp = 320, heightDp = 640)
@Composable
private fun GroupCardStatesPreview() {
    AppTheme {
        Column(
            Modifier.background(AppColors.GroupBackground).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (HomeFixtures.exploreState.content as GroupExploreContent.Ready).items.forEach { item ->
                GroupCatalogCard(item.group, item.joinType, item.isJoined, {})
            }
        }
    }
}

@Preview(name = "그룹 카드 · 긴 이름·많은 인원·검색 강조", widthDp = 320, heightDp = 440)
@Composable
private fun GroupCardLongNamePreview() {
    AppTheme {
        Column(
            Modifier.background(AppColors.GroupBackground).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val item = (HomeFixtures.exploreState.content as GroupExploreContent.Ready).items.first()
            GroupCatalogCard(
                group = item.group.copy(name = "퇴근 후 함께 산책하며 하루의 감정을 기록하는 아주 긴 이름의 그룹", memberCount = 123456),
                joinType = item.joinType,
                isJoined = true,
                onClick = {},
                highlightQuery = "산책",
            )
            GroupCatalogCard(
                group = item.group.copy(name = "퇴근 후 함께 산책하며 하루의 감정을 기록하는 아주 긴 이름의 그룹"),
                joinType = GroupJoinType.PRIVATE,
                isJoined = false,
                onClick = {},
            )
            GroupListItem(
                HomeFixtures.groups.first().copy(name = "우리 동네 사람들과 천천히 오래 함께 걸어가는 아주 긴 그룹 이름"),
                {},
            )
        }
    }
}

@Preview(name = "내 그룹 카드 · 체크 없음", widthDp = 360, heightDp = 360)
@Preview(name = "내 그룹 카드 · 좁은 화면", widthDp = 320, heightDp = 360)
@Composable
private fun MyGroupCardsWithoutBadgePreview() {
    AppTheme {
        Column(
            Modifier.background(AppColors.GroupBackground).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeFixtures.groups.forEach { group -> GroupListItem(group, {}) }
        }
    }
}

@Preview(name = "참여 체크 비교 · 공개·비공개·내 그룹", widthDp = 320, heightDp = 560)
@Composable
private fun GroupJoinedCheckComparisonPreview() {
    AppTheme {
        Column(
            Modifier.background(AppColors.GroupBackground).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val group = HomeFixtures.groups.first()
            GroupCatalogCard(group, GroupJoinType.PUBLIC, true, {})
            GroupCatalogCard(group, GroupJoinType.PRIVATE, true, {})
            GroupCatalogCard(group, GroupJoinType.PUBLIC, false, {})
            GroupCatalogCard(group, GroupJoinType.PRIVATE, false, {})
            GroupListItem(group, {})
        }
    }
}
