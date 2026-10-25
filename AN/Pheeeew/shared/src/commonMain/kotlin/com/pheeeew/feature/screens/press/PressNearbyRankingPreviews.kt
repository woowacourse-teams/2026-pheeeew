package com.pheeeew.feature.screens.press

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.press.component.PressNearbyRankingCard

private fun nearbyPreviewRows() =
    listOf(
        PressNearbyRankRow(9, "예민한 고양이", 12),
        PressNearbyRankRow(10, "닉네임", 10, isMe = true),
        PressNearbyRankRow(11, "조용한 오리", 9),
    )

@Preview(name = "주변 순위판 상태", widthDp = 360, heightDp = 1200)
@Composable
private fun NearbyRankingStatesPreview() {
    AppTheme {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PressNearbyRankingCard(PressNearbyRankingUiState(nearbyPreviewRows(), progress = 0.22f))
            PressNearbyRankingCard(
                PressNearbyRankingUiState(
                    listOf(
                        PressNearbyRankRow(1, "예민한 고양이", 12),
                        PressNearbyRankRow(2, "나", 10, true),
                        PressNearbyRankRow(2, "공동 순위", 10),
                    ),
                    progress = 1f,
                ),
            )
            PressNearbyRankingCard(PressNearbyRankingUiState(status = PressNearbyRankingStatus.Loading))
            PressNearbyRankingCard(PressNearbyRankingUiState())
            PressNearbyRankingCard(PressNearbyRankingUiState(participationPending = true))
            PressNearbyRankingCard(PressNearbyRankingUiState(nearbyPreviewRows().take(2), participationPending = false))
            PressNearbyRankingCard(
                PressNearbyRankingUiState(nearbyPreviewRows(), PressNearbyRankingStatus.Reconnecting),
            )
            PressNearbyRankingCard(
                PressNearbyRankingUiState(listOf(PressNearbyRankRow(10000, "아주 긴 닉네임을 사용하는 참가자", Long.MAX_VALUE))),
            )
            PressNearbyRankingCard(
                PressNearbyRankingUiState(listOf(PressNearbyRankRow(1, "같은 이름", 1)), participationPending = true),
            )
        }
    }
}

@Preview(name = "프레스 주변 순위판", widthDp = 402, heightDp = 876)
@Preview(name = "프레스 작은 화면", widthDp = 320, heightDp = 640)
@Composable
private fun NearbyRankingScreenPreview() {
    AppTheme {
        PressScreen(
            PressUiState(isLoadingMy = false, isLoadingAll = false),
            { true },
            {},
            nearbyRanking = PressNearbyRankingUiState(nearbyPreviewRows(), progress = 0.22f),
        )
    }
}

@Preview(name = "주변 순위판 큰 글씨", widthDp = 320, heightDp = 400)
@Composable
private fun NearbyRankingLargeTextPreview() {
    AppTheme {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
            PressNearbyRankingCard(PressNearbyRankingUiState(nearbyPreviewRows()), Modifier.padding(16.dp))
        }
    }
}
