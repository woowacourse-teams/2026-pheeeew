package com.pheeeew.feature.screens.map.filter

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

enum class FilterPeriod { TwoWeeks, OneMonth, All, Custom }

enum class FilterContent { All, Memo, Recording }

enum class FilterScope { All, Mine }

enum class FilterPage { Filters, StartDate, EndDate }

data class FilterDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<FilterDate> {
    override fun compareTo(other: FilterDate): Int =
        compareValuesBy(this, other, FilterDate::year, FilterDate::month, FilterDate::day)

    override fun toString(): String = "$year.${month.toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"
}

data class MapFilterUiModel(
    val visible: Boolean = false,
    val page: FilterPage = FilterPage.Filters,
    val period: FilterPeriod = FilterPeriod.OneMonth,
    val content: FilterContent = FilterContent.All,
    val scope: FilterScope = FilterScope.All,
    val startDate: FilterDate? = null,
    val endDate: FilterDate? = null,
    val applying: Boolean = false,
    val applyError: String? = null,
)

internal fun todayForFilter(): FilterDate {
    val date = (Clock.System.now() + 9.hours).toString().take(10).split('-')
    return FilterDate(date[0].toInt(), date[1].toInt(), date[2].toInt())
}

internal fun daysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
