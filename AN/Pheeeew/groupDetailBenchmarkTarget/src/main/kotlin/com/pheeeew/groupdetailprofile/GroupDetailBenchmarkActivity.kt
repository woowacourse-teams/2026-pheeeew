package com.pheeeew.groupdetailprofile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.detail.GroupDetailActions
import com.pheeeew.feature.screens.group.detail.GroupDetailContent
import com.pheeeew.feature.screens.group.detail.GroupDetailScreen
import com.pheeeew.feature.screens.group.detail.GroupDetailUiState
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

class GroupDetailBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            var detail by remember { mutableStateOf(benchmarkDetail()) }
            AppTheme {
                GroupDetailScreen(
                    uiState = GroupDetailUiState(content = GroupDetailContent.Ready(detail)),
                    actions =
                        GroupDetailActions(
                            onBack = { finish() },
                            onReturnHome = { finish() },
                            onRetry = {},
                            onMoreClick = {},
                            onInviteClick = {},
                            onCopyCodeClick = {},
                            onDismissOverlay = {},
                            onLeaveMenuClick = {},
                            onConfirmLeave = {},
                            onRetryLeave = {},
                            onResolveLeaveOutcome = {},
                            onNoticeDismissed = {},
                            onMoodReactionClick = null,
                            onMoodAudioClick = null,
                            onMoodBlockClick = null,
                            onMoodReportClick = null,
                            onMoodFeedRetry = {
                                detail = detail.copy(weeklyEmotionPressCount = detail.weeklyEmotionPressCount + 1L)
                            },
                            onMoodFeedLoadMore = null,
                        ),
                )
            }
        }
    }
}

private fun benchmarkDetail() =
    GroupDetailUiModel(
        group =
            GroupSummaryUiModel(
                id = GroupId("macrobenchmark-group"),
                name = "프로파일링 그룹",
                memberCount = 12L,
                weeklyStampCount = 128L,
                stamp = StampAppearanceUiModel("측정", StampShapeId.CIRCLE, 0xFF9DE8D0L, 0xFF15181BL),
            ),
        role = GroupRole.MEMBER,
        inviteCode = "PROFIL",
        weeklyStampCount = 128L,
        weeklyStampRank = 2,
        weeklyEmotionPressCount = 98L,
        weeklyEmotionPressRank = 2,
    )
