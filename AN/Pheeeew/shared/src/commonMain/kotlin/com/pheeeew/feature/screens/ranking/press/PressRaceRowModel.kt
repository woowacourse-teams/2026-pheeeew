package com.pheeeew.feature.screens.ranking.press

import com.pheeeew.feature.emotion.model.EmotionKind

internal data class PressRaceRowModel(
    val rank: Long,
    val nickname: String,
    val count: Long,
    val progress: Float,
    val isMe: Boolean = false,
    val emotion: EmotionKind? = null,
    val publicId: String? = null,
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
            isMe = person.isMe,
            publicId = person.publicId,
            emotion = person.emotion,
            markerIndex = index % 3,
        )
    }
}

internal fun raceProgress(
    count: Long,
    maximum: Long,
): Float = if (maximum <= 0L) 0f else (count.toDouble() / maximum.toDouble()).coerceIn(0.0, 1.0).toFloat()

internal enum class RaceCountUnit { Exact, Man, Eok, Jo, Gyeong }

internal data class CompactRaceCount(
    val number: String,
    val unit: RaceCountUnit,
)

internal fun compactRaceCount(count: Long): CompactRaceCount {
    val unit =
        listOf(
            10_000_000_000_000_000L to RaceCountUnit.Gyeong,
            1_000_000_000_000L to RaceCountUnit.Jo,
            100_000_000L to RaceCountUnit.Eok,
            10_000L to RaceCountUnit.Man,
        ).firstOrNull { count >= it.first } ?: return CompactRaceCount(count.toString(), RaceCountUnit.Exact)
    val whole = count / unit.first
    val tenth = (count % unit.first) / (unit.first / 10L)
    return CompactRaceCount(if (tenth == 0L) whole.toString() else "$whole.$tenth", unit.second)
}

// Compress large score gaps for display; no participant is placed at a finish line.
internal fun raceTrackPosition(progress: Float): Float =
    0.25f + kotlin.math.sqrt(progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f) * 0.45f
