package com.pheeeew.feature.screens.ranking.press

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.core.designsystem.theme.AppTheme

private val raceSample =
    listOf(
        "예민한 고양이",
        "나",
        "조용한 오리",
        "졸린 토끼",
        "느긋한 곰",
        "수줍은 복숭아",
        "멍한 구름",
        "씩씩한 강아지",
        "작은 고슴도치",
        "말랑한 두부",
        "신중한 펭귄",
        "웃는 감자",
        "차분한 여우",
    ).mapIndexed { index, name ->
        val counts = listOf(12L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 3L, 2L, 1L, 1L)
        PressRaceRowModel(index + 1, name, counts[index], raceProgress(counts[index], 12L), index == 1, index % 3)
    }

@Preview(name = "전체 순위 SVG", widthDp = 402, heightDp = 874)
@Preview(name = "전체 순위 작은 화면", widthDp = 320, heightDp = 720)
@Preview(name = "전체 순위 큰 글씨", widthDp = 402, heightDp = 874, fontScale = 1.5f)
@Composable
private fun PressRacePreview() {
    AppTheme {
        PressRankingScreen({}, PressRankingUiState(status = PressRankingStatus.Ready), {}, {}, rows = raceSample)
    }
}

@Preview(name = "전체 순위 빈 목록", widthDp = 402, heightDp = 874)
@Composable
private fun PressRaceEmptyPreview() {
    AppTheme {
        PressRankingScreen({}, PressRankingUiState(status = PressRankingStatus.Ready), {}, {})
    }
}

@Preview(name = "전체 순위 로딩", widthDp = 402, heightDp = 874)
@Composable
private fun PressRaceLoadingPreview() {
    AppTheme { PressRankingScreen({}, PressRankingUiState(), {}, {}) }
}

@Preview(name = "전체 순위 실패", widthDp = 402, heightDp = 874)
@Composable
private fun PressRaceFailedPreview() {
    AppTheme {
        PressRankingScreen({}, PressRankingUiState(status = PressRankingStatus.Failed), {}, {})
    }
}

@Preview(name = "전체 순위 긴 값과 동점", widthDp = 320, heightDp = 720)
@Composable
private fun PressRaceBoundaryPreview() {
    val rows =
        listOf(
            PressRaceRowModel(10000, "아주아주아주아주긴중복닉네임", Long.MAX_VALUE, 1f),
            PressRaceRowModel(10000, "아주아주아주아주긴중복닉네임", Long.MAX_VALUE, 1f, isMe = true),
            PressRaceRowModel(10002, "0회 참여자", 0L, 0f),
        )
    AppTheme {
        PressRankingScreen({}, PressRankingUiState(status = PressRankingStatus.Ready), {}, {}, rows = rows)
    }
}

@Preview(name = "전체 순위 100명", widthDp = 402, heightDp = 874)
@Composable
private fun PressRaceLongListPreview() {
    val rows =
        List(100) { index ->
            PressRaceRowModel(index + 1, "참여자 ${index + 1}", (100 - index).toLong(), (100 - index) / 100f)
        }
    AppTheme {
        PressRankingScreen({}, PressRankingUiState(status = PressRankingStatus.Ready), {}, {}, rows = rows)
    }
}

@Preview(name = "전체 순위 1210회", widthDp = 320, heightDp = 720)
@Preview(name = "전체 순위 1210회 큰 글씨", widthDp = 320, heightDp = 720, fontScale = 1.5f)
@Composable
private fun PressRaceFourDigitCountPreview() {
    AppTheme {
        PressRankingScreen(
            {},
            PressRankingUiState(status = PressRankingStatus.Ready),
            {},
            {},
            rows =
                listOf(
                    PressRaceRowModel(1, "예민한 고양이", 1210L, 1f),
                    PressRaceRowModel(2, "나", 1000L, raceProgress(1000L, 1210L), isMe = true),
                    PressRaceRowModel(3, "조용한 오리", 999L, raceProgress(999L, 1210L)),
                ),
        )
    }
}
