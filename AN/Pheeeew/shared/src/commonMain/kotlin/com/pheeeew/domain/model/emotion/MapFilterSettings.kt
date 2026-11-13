package com.pheeeew.domain.model.emotion

import kotlin.time.Duration.Companion.hours

enum class MapFilterPeriod { TWO_WEEKS, ONE_MONTH, ALL, CUSTOM }

enum class MapFilterContent { ALL, MEMO, AUDIO }

enum class MapFilterScope { ALL, MY }

data class SearchDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<SearchDate> {
    init {
        require(year in 1..9999)
        require(month in 1..12)
        require(day in 1..daysInMonth(year, month))
    }

    override fun compareTo(other: SearchDate): Int =
        compareValuesBy(this, other, SearchDate::year, SearchDate::month, SearchDate::day)

    fun toIsoString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    fun minusDays(days: Int): SearchDate {
        var result = this
        repeat(days) {
            result =
                if (result.day > 1) {
                    result.copy(day = result.day - 1)
                } else {
                    val previousMonth = if (result.month == 1) 12 else result.month - 1
                    val previousYear = if (result.month == 1) result.year - 1 else result.year
                    SearchDate(previousYear, previousMonth, daysInMonth(previousYear, previousMonth))
                }
        }
        return result
    }

    fun minusOneMonth(): SearchDate {
        val month = if (month == 1) 12 else month - 1
        val year = if (this.month == 1) year - 1 else year
        return SearchDate(year, month, day.coerceAtMost(daysInMonth(year, month)))
    }

    companion object {
        fun todayKst(): SearchDate {
            val date =
                (
                    kotlin.time.Clock.System
                        .now() + 9.hours
                ).toString().take(10).split('-')
            return SearchDate(date[0].toInt(), date[1].toInt(), date[2].toInt())
        }

        fun parse(value: String): SearchDate? =
            runCatching {
                val parts = value.split('-')
                require(parts.size == 3)
                SearchDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }.getOrNull()
    }
}

data class MapFilterSettings(
    val period: MapFilterPeriod = MapFilterPeriod.ONE_MONTH,
    val customStartDate: SearchDate? = null,
    val customEndDate: SearchDate? = null,
    val content: MapFilterContent = MapFilterContent.ALL,
    val scope: MapFilterScope = MapFilterScope.ALL,
) {
    init {
        require((customStartDate == null) == (customEndDate == null))
        require(customStartDate == null || customStartDate <= customEndDate!!)
        require(period != MapFilterPeriod.CUSTOM || customStartDate != null)
    }

    fun toSearchFilter(today: SearchDate = SearchDate.todayKst()): EmotionSearchFilter {
        val dates =
            when (period) {
                MapFilterPeriod.TWO_WEEKS -> today.minusDays(13) to today
                MapFilterPeriod.ONE_MONTH -> today.minusOneMonth() to today
                MapFilterPeriod.ALL -> null to null
                MapFilterPeriod.CUSTOM -> customStartDate to customEndDate
            }
        return EmotionSearchFilter(
            startDate = dates.first?.toIsoString(),
            endDate = dates.second?.toIsoString(),
            content = content,
            scope = scope,
        )
    }

    companion object {
        val DEFAULT = MapFilterSettings()
    }
}

data class EmotionSearchFilter(
    val startDate: String?,
    val endDate: String?,
    val content: MapFilterContent,
    val scope: MapFilterScope,
) {
    init {
        require((startDate == null) == (endDate == null))
    }

    companion object {
        val DEFAULT = MapFilterSettings.DEFAULT.toSearchFilter()
    }
}

private fun daysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
