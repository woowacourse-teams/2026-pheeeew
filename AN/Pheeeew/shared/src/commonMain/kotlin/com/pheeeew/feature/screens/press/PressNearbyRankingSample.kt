package com.pheeeew.feature.screens.press

/**
 * Temporary screen data explicitly used for #925 UI verification before API integration.
 * Independent of real input, account nickname, and server totals.
 * Replace the Route's state source with mapped API state; the card/model need no transport dependency.
 */
internal val temporaryNearbyRanking =
    PressNearbyRankingUiState(
        rows =
            listOf(
                PressNearbyRankRow(rank = 9, nickname = "예민한 고양이", count = 12, publicId = "sample-cat"),
                PressNearbyRankRow(rank = 10, nickname = "나", count = 10, isMe = true, publicId = "sample-me"),
                PressNearbyRankRow(rank = 11, nickname = "조용한 오리", count = 9, publicId = "sample-owl"),
            ),
        progress = 0.22f,
    )
