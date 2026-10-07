@file:OptIn(ExperimentalForeignApi::class)

package com.pheeeew

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.detail.GroupDetailActions
import com.pheeeew.feature.screens.group.detail.GroupDetailContent
import com.pheeeew.feature.screens.group.detail.GroupDetailScreen
import com.pheeeew.feature.screens.group.detail.GroupDetailUiState
import com.pheeeew.feature.screens.group.detail.previewGroupDetailUiModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.Foundation.NSNotificationCenter
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Suppress("ktlint:standard:function-naming")
fun GroupDetailProfileViewController() =
    ComposeUIViewController {
        val detailState =
            remember {
                mutableStateOf(
                    previewGroupDetailUiModel(GroupRole.MEMBER).copy(weeklyEmotionPressCount = INITIAL_PRESS_COUNT),
                )
            }
        val pendingTapMarks = remember { mutableListOf<TimeMark>() }
        val renderLatencyMicros = remember { mutableListOf<Long>() }
        var updates by remember { mutableIntStateOf(0) }

        val updateStatistics: () -> Unit = {
            pendingTapMarks += TimeSource.Monotonic.markNow()
            updates++
            val current = detailState.value
            detailState.value = current.copy(weeklyEmotionPressCount = current.weeklyEmotionPressCount + 1L)
        }

        SideEffect {
            while (pendingTapMarks.isNotEmpty()) {
                renderLatencyMicros += pendingTapMarks.removeAt(0).elapsedNow().inWholeMicroseconds
            }
        }

        LaunchedEffect(Unit) {
            delay(PROFILE_WARMUP_MILLIS)
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = PROFILE_STARTED_NOTIFICATION,
                `object` = null,
            )
            repeat(PROFILE_TAP_COUNT) {
                updateStatistics()
                delay(PROFILE_TAP_INTERVAL_MILLIS)
            }
            withFrameNanos { }
            println(
                "IOS_GROUP_DETAIL_PROFILE updates=$updates " +
                    "state_to_compose_p50_ms=${renderLatencyMicros.percentileMillisFromMicros(50)} " +
                    "state_to_compose_p95_ms=${renderLatencyMicros.percentileMillisFromMicros(95)}",
            )
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = PROFILE_FINISHED_NOTIFICATION,
                `object` = null,
            )
        }

        AppTheme {
            GroupDetailScreen(
                uiState = GroupDetailUiState(content = GroupDetailContent.Ready(detailState.value)),
                actions =
                    GroupDetailActions(
                        onBack = {},
                        onReturnHome = {},
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
                        onMoodFeedRetry = null,
                        onMoodFeedLoadMore = null,
                    ),
            )
        }
    }

private fun List<Long>.percentileMillisFromMicros(percentile: Int): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val index = (((percentile.coerceIn(0, 100) / 100.0) * (sorted.size - 1)).toInt()).coerceIn(sorted.indices)
    return sorted[index] / MICROS_PER_MILLI.toDouble()
}

private const val PROFILE_STARTED_NOTIFICATION = "com.pheeeew.group-detail-profile.started"
private const val PROFILE_FINISHED_NOTIFICATION = "com.pheeeew.group-detail-profile.finished"
private const val INITIAL_PRESS_COUNT = 1_238L
private const val PROFILE_WARMUP_MILLIS = 1_500L
private const val PROFILE_TAP_COUNT = 200
private const val PROFILE_TAP_INTERVAL_MILLIS = 8L
private const val MICROS_PER_MILLI = 1_000L
