package com.pheeeew.feature.screens.press

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.press.component.PressActivityFooter
import com.pheeeew.feature.screens.press.component.PressNearbyRankingCard

private val activityPreviewScenarios =
    listOf(
        temporaryPressActivity,
        PressActivityUiState(0, PressActivityStatus.Known),
        PressActivityUiState(status = PressActivityStatus.Loading),
        PressActivityUiState(),
        PressActivityUiState(status = PressActivityStatus.Reconnecting),
        PressActivityUiState(13, PressActivityStatus.Known, PressRaceNotice(2, overtaken = 2)),
        PressActivityUiState(13, PressActivityStatus.Known, PressRaceNotice(3, overtakenBy = 1)),
        PressActivityUiState(13, PressActivityStatus.Known, PressRaceNotice(4, overtaken = 2, overtakenBy = 1)),
        PressActivityUiState(Long.MAX_VALUE, PressActivityStatus.Known, PressRaceNotice(5, overtaken = Long.MAX_VALUE)),
    )

@Preview(name = "활동·알림 전체 상태", widthDp = 360, heightDp = 1400)
@Composable
private fun ActivityStatesPreview() {
    AppTheme {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            activityPreviewScenarios.forEach { PressActivityFooter(it, reducedMotionOverride = true) }
            PressNearbyRankingCard(temporaryNearbyRanking, activity = temporaryPressActivity)
        }
    }
}

@Preview(name = "활동 포함 프레스", widthDp = 402, heightDp = 876)
@Preview(name = "활동 포함 작은 프레스", widthDp = 320, heightDp = 640)
@Composable
private fun ActivityScreenPreview() {
    AppTheme {
        PressScreen(
            PressUiState(isLoadingMy = false, isLoadingAll = false),
            { true },
            {},
            nearbyRanking = temporaryNearbyRanking,
            activity = temporaryPressActivity,
        )
    }
}

@Preview(name = "활동 큰 글씨·양방향", widthDp = 320, heightDp = 400)
@Composable
private fun ActivityLargeTextPreview() {
    AppTheme {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
            PressActivityFooter(activityPreviewScenarios[7], reducedMotionOverride = true)
        }
    }
}

/** Interactive Preview: state replaces the previous notice; no timers or real input are involved. */
@Preview(name = "알림 교체·제거 데모", widthDp = 360, heightDp = 500)
@Composable
private fun ActivityReplacementPreview() {
    AppTheme {
        var index by remember { mutableIntStateOf(0) }
        var occurrence by remember { mutableIntStateOf(0) }
        val state = activityPreviewScenarios[index]
        Column {
            PressNearbyRankingCard(
                temporaryNearbyRanking,
                activity = state.copy(notice = state.notice?.copy(displayId = occurrence.toLong())),
            )
            FlowRow {
                TextButton(onClick = {
                    index = (index + 1) % activityPreviewScenarios.size
                    occurrence++
                }) { Text("다음 상태") }
                TextButton(onClick = { occurrence++ }) { Text("동일 문구 새 알림") }
                TextButton(onClick = {
                    index = 1
                    occurrence++
                }) { Text("알림 제거") }
            }
        }
    }
}

@Preview(name = "활동 인원 0~5명", widthDp = 360, heightDp = 360)
@Composable
private fun ActivityAvatarCountPreview() {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(12.dp)) {
            (0L..5L).forEach { count ->
                PressActivityFooter(
                    PressActivityUiState(count, PressActivityStatus.Known),
                    reducedMotionOverride = true,
                )
            }
        }
    }
}
