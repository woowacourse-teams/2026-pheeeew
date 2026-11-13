package com.pheeeew.feature.screens.map.detail

import kotlin.time.Instant

internal fun formatEmotionCreatedAt(
    createdAt: String,
    now: Instant,
): String {
    val createdAtInstant = runCatching { Instant.parse(createdAt) }.getOrNull()
    if (createdAtInstant == null) return createdAt.toDateLabel()

    val elapsedMinutes = (now - createdAtInstant).inWholeMinutes
    return when {
        elapsedMinutes < 1 -> "방금전"
        elapsedMinutes < 60 -> "${elapsedMinutes}분전"
        elapsedMinutes < 24L * 60L -> "${elapsedMinutes / 60}시간 전"
        else -> createdAt.toDateLabel()
    }
}

private fun String.toDateLabel(): String =
    substringBefore('T')
        .takeIf { it.length == 10 && it[4] == '-' && it[7] == '-' }
        ?.replace('-', '.')
        ?: this
