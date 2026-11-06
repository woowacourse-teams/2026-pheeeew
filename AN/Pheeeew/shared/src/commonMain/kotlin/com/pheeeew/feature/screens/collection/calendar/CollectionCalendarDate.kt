package com.pheeeew.feature.screens.collection.calendar

import com.pheeeew.core.time.koreaCalendarDate
import kotlin.time.Clock

internal data class CollectionCalendarDay(
    val date: String?,
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean,
)

internal fun currentCollectionTodayDate(): String = koreaCalendarDate(Clock.System.now().toEpochMilliseconds())

internal fun currentCollectionMonth(): String = currentCollectionTodayDate().take(7)

internal fun collectionMonthIndex(month: String): Int =
    (month.substring(0, 4).toInt() - 1) * 12 + month.substring(5, 7).toInt() - 1

internal fun collectionMonthFromIndex(index: Int): String {
    val year = index / 12 + 1
    val month = index % 12 + 1
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"
}

internal fun collectionMonthDays(month: String): List<CollectionCalendarDay> {
    val year = month.substring(0, 4).toInt()
    val monthNumber = month.substring(5, 7).toInt()
    val daysInMonth = collectionDaysInMonth(year, monthNumber)
    val leadingDays = collectionWeekday("$month-01")
    val dayCount = ((leadingDays + daysInMonth + 6) / 7) * 7
    val previousMonth = shiftCollectionMonth(month, -1)
    val nextMonth = shiftCollectionMonth(month, 1)
    val previousMonthDays =
        previousMonth?.let {
            collectionDaysInMonth(it.substring(0, 4).toInt(), it.substring(5, 7).toInt())
        }
    return List(dayCount) { index ->
        val day = index - leadingDays + 1
        when {
            day in 1..daysInMonth -> {
                CollectionCalendarDay("$month-${day.toString().padStart(2, '0')}", day, true)
            }

            day < 1 -> {
                val adjacentDate =
                    previousMonth?.let {
                        "$it-${(requireNotNull(previousMonthDays) + day).toString().padStart(2, '0')}"
                    }
                CollectionCalendarDay(adjacentDate, previousMonthDays?.plus(day) ?: day, false)
            }

            else -> {
                val adjacentDate = nextMonth?.let { "$it-${(day - daysInMonth).toString().padStart(2, '0')}" }
                CollectionCalendarDay(adjacentDate, day - daysInMonth, false)
            }
        }
    }
}

internal fun shiftCollectionMonth(
    month: String,
    amount: Int,
): String? {
    val year = month.substring(0, 4).toInt()
    val monthNumber = month.substring(5, 7).toInt()
    val absoluteMonth = (year - 1L) * 12L + monthNumber - 1L + amount
    if (absoluteMonth !in 0 until 9999L * 12L) return null
    val shiftedYear = (absoluteMonth / 12L).toInt() + 1
    val shiftedMonth = (absoluteMonth % 12L).toInt() + 1
    return "${shiftedYear.toString().padStart(4, '0')}-${shiftedMonth.toString().padStart(2, '0')}"
}

internal fun collectionDayOfWeek(date: String): String =
    listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")[collectionWeekday(date)]

internal fun collectionWeekday(date: String): Int {
    val year = date.substring(0, 4).toInt()
    val month = date.substring(5, 7).toInt()
    val day = date.substring(8, 10).toInt()
    val monthOffsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val adjustedYear = if (month < 3) year - 1 else year
    return (adjustedYear + adjustedYear / 4 - adjustedYear / 100 + adjustedYear / 400 + monthOffsets[month - 1] + day) %
        7
}

private fun collectionDaysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
