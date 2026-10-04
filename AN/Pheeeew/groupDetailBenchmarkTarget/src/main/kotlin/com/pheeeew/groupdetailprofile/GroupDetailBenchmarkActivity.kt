package com.pheeeew.groupdetailprofile

import android.os.Bundle
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
import com.pheeeew.feature.screens.group.detail.model.EmotionCountUiModel
import com.pheeeew.feature.screens.group.detail.model.EmotionKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailCopyKey
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationKind
import com.pheeeew.feature.screens.group.detail.model.GroupDetailPresentationUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupDetailUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupRankUiModel
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** Macrobenchmark target for the production detail composable. Every press stays in local state. */
class GroupDetailBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                            onEmotionTap = { emotion ->
                                detail =
                                    detail.copy(
                                        todayTotal = detail.todayTotal + 1L,
                                        emotionCounts =
                                            detail.emotionCounts.map { count ->
                                                if (count.kind == emotion) {
                                                    count.copy(count = count.count + 1L)
                                                } else {
                                                    count
                                                }
                                            },
                                    )
                                true
                            },
                            onResolvePressOutcome = {},
                            onNoticeDismissed = {},
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
        emotionCounts = EmotionKind.entries.mapIndexed { index, kind -> EmotionCountUiModel(kind, COUNTS[index]) },
        todayTotal = COUNTS.sum(),
        rank = GroupRankUiModel.Ranked(2),
        inviteCode = "PROFIL",
        presentation =
            GroupDetailPresentationUiModel(
                kind = GroupDetailPresentationKind.Active,
                heroTitle = GroupDetailCopyKey.ActiveHeroTitle,
                heroSubtitle = GroupDetailCopyKey.ActiveHeroSubtitle,
                summaryMessage = GroupDetailCopyKey.SummaryBlocked,
            ),
    )

private val COUNTS = listOf(428L, 312L, 246L, 154L, 98L)
