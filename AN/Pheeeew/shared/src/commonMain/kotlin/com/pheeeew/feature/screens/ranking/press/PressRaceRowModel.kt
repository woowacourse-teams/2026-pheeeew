package com.pheeeew.feature.screens.ranking.press

internal data class PressRaceRowModel(
    val rank: Int,
    val nickname: String,
    val count: Long,
    val progress: Float,
    val isMe: Boolean = false,
    val markerIndex: Int = 0,
)

internal fun List<PressPersonRank>.toRaceRows(): List<PressRaceRowModel> {
    val maximum = maxOfOrNull { it.count } ?: 0L
    return mapIndexed { index, person ->
        PressRaceRowModel(
            person.rank,
            person.nickname,
            person.count,
            raceProgress(person.count, maximum),
            markerIndex =
                index % 3,
        )
    }
}

internal fun raceProgress(
    count: Long,
    maximum: Long,
): Float = if (maximum <= 0L) 0f else (count.toDouble() / maximum.toDouble()).coerceIn(0.0, 1.0).toFloat()
