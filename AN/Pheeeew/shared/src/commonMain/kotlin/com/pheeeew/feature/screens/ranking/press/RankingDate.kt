package com.pheeeew.feature.screens.ranking.press

import com.pheeeew.core.time.koreaCalendarDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal fun rankingToday(): String = koreaCalendarDate(Clock.System.now().toEpochMilliseconds())

internal fun shiftRankingDate(
    date: String,
    days: Int,
): String = (Instant.parse("${date}T00:00:00Z") + days.days).toString().substringBefore('T')

internal fun rankingWeekday(date: String): Int =
    ((Instant.parse("${date}T00:00:00Z").epochSeconds / 86_400 + 4).mod(7)).toInt()

internal fun rankingDateLabel(date: String): String = "${date.replace('-', '.')} (${"일월화수목금토"[rankingWeekday(date)]})"

internal fun rankingMonthDays(month: String): List<String> {
    val first = "$month-01"
    return (0..30).map { shiftRankingDate(first, it) }.takeWhile { it.startsWith(month) }
}

internal fun shiftRankingMonth(
    month: String,
    offset: Int,
): String {
    val index = month.take(4).toInt() * 12 + month.takeLast(2).toInt() - 1 + offset
    return "${(index / 12).toString().padStart(4, '0')}-${(index.mod(12) + 1).toString().padStart(2, '0')}"
}
