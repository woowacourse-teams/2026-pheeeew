package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.component.GroupListSkeleton
import com.pheeeew.feature.screens.group.create.GroupJoinTypeSelector
import com.pheeeew.feature.screens.group.create.component.CreateTextField
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinCallToAction
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinFailure
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinPhase
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinUiState
import com.pheeeew.feature.screens.group.detail.GroupProfile
import com.pheeeew.feature.screens.group.detail.component.GroupCopyFeedbackButton
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.detail.model.toReadyUiModel
import com.pheeeew.feature.screens.group.home.GroupHomeScreen
import com.pheeeew.feature.screens.group.home.GroupHomeTab
import com.pheeeew.feature.screens.group.home.explore.GroupExploreContent
import com.pheeeew.feature.screens.group.model.GroupJoinType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview(name = "탭별 검색 · 검색어·최근 검색 독립 유지", widthDp = 360, heightDp = 800)
@Preview(name = "모션 · 검색 좁은 화면", widthDp = 320, heightDp = 640)
@Composable
private fun GroupSearchInteractivePreview() {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(GroupHomeTab.MyGroups) }
    var explore by remember { mutableStateOf(HomeFixtures.exploreState) }
    LaunchedEffect(query) {
        delay(300)
        val source = (HomeFixtures.exploreState.content as GroupExploreContent.Ready).items
        val matches = source.filter { it.group.name.contains(query.trim(), ignoreCase = true) }
        explore =
            explore.copy(
                query = query,
                content =
                    if (matches.isEmpty()) {
                        GroupExploreContent.Empty
                    } else {
                        GroupExploreContent.Ready(matches, null)
                    },
            )
    }
    AppTheme {
        GroupHomeScreen(
            uiState = HomeFixtures.listState,
            onCreateClick = {},
            onJoinClick = {},
            onGroupClick = {},
            onRetry = {},
            selectedTab = tab,
            onTabSelected = { tab = it },
            exploreUiState = explore,
            onExploreQueryChanged = {
                query = it
                explore = explore.copy(query = it, content = GroupExploreContent.Loading)
            },
        )
    }
}

@Preview(name = "모션 · 생성 선택·오류·복사", widthDp = 360, heightDp = 560)
@Composable
private fun GroupFormMotionPreview() {
    var joinType by remember { mutableStateOf(GroupJoinType.PRIVATE) }
    var value by remember { mutableStateOf("산책 모임") }
    var error by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    AppTheme {
        Column(
            Modifier.fillMaxSize().background(AppColors.GroupBackground).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GroupJoinTypeSelector(joinType, true) { joinType = it }
            CreateTextField(
                label = "그룹 이름",
                value = value,
                placeholder = "그룹 이름을 입력하세요",
                maximumLength = 20,
                currentLength = value.length,
                errorText = if (error) "이미 사용 중인 그룹 이름이에요" else null,
                onValueChange = { value = it },
            )
            TextButton(onClick = { error = !error }) { Text("오류 표시 전환") }
            GroupCopyFeedbackButton(copied) { copied = !copied }
        }
    }
}

@Preview(name = "모션 · 참여 완료 전환 데모", widthDp = 360, heightDp = 500)
@Composable
private fun GroupMembershipMotionPreview() {
    var member by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(GroupDetailJoinPhase.Idle) }
    val scope = rememberCoroutineScope()
    AppTheme {
        Column(Modifier.fillMaxSize().background(AppColors.GroupBackground).padding(24.dp)) {
            TextButton(onClick = {
                member = false
                phase = GroupDetailJoinPhase.Idle
                failure = false
            }) { Text("참여 데모 초기화") }
            TextButton(onClick = { failure = !failure }) { Text("오류 안내 전환") }
            GroupProfile(
                group =
                    previewGroupDetailUiModel(if (member) GroupRole.MEMBER else GroupRole.NONE)
                        .copy(joinType = GroupJoinType.PUBLIC)
                        .toReadyUiModel(GroupMoodFeedUiState.Loading)
                        .let {
                            it.copy(
                                memberCount =
                                    it.memberCount + if (member) 1 else 0,
                            )
                        },
                onInviteClick = {},
                onJoinClick = {
                    scope.launch {
                        phase = GroupDetailJoinPhase.Submitting
                        delay(800)
                        phase = GroupDetailJoinPhase.Completed
                        delay(400)
                        member = true
                    }
                },
                joinState =
                    GroupDetailJoinUiState(
                        if (failure) GroupDetailJoinFailure.ApiIntegrationPending else null,
                        phase,
                    ),
            )
        }
    }
}

@Preview(name = "목록 · 그룹 스켈레톤", widthDp = 360, heightDp = 400)
@Composable
private fun GroupSkeletonPreview() {
    AppTheme { GroupListSkeleton(Modifier.background(AppColors.GroupBackground).padding(24.dp)) }
}

@Preview(name = "목록 · 멤버 스켈레톤", widthDp = 320, heightDp = 320)
@Composable
private fun MemberSkeletonPreview() {
    AppTheme { GroupListSkeleton(Modifier.background(AppColors.GroupBackground).padding(24.dp), memberRows = true) }
}

@Preview(name = "참여 버튼 · 기본", widthDp = 160, heightDp = 80)
@Composable
private fun JoinIdlePreview() {
    AppTheme { GroupDetailJoinCallToAction({}, GroupDetailJoinPhase.Idle) }
}

@Preview(name = "참여 버튼 · 처리 중", widthDp = 160, heightDp = 80)
@Composable
private fun JoinSubmittingPreview() {
    AppTheme { GroupDetailJoinCallToAction({}, GroupDetailJoinPhase.Submitting) }
}

@Preview(name = "참여 버튼 · 완료", widthDp = 160, heightDp = 80)
@Composable
private fun JoinCompletedPreview() {
    AppTheme { GroupDetailJoinCallToAction({}, GroupDetailJoinPhase.Completed) }
}
