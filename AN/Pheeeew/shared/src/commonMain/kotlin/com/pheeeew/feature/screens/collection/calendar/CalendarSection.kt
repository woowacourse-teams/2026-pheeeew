package com.pheeeew.feature.screens.collection.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_back
import pheeeew.shared.generated.resources.ic_arrow_forward

private const val CALENDAR_GRID_WEEKS = 6
private const val CALENDAR_WEEK_DAYS = 7
private const val CALENDAR_CELL_ASPECT_RATIO = 0.82f

@Composable
fun CalendarSection(
    month: String,
    cards: List<CollectionCardUiModel>,
    isLoading: Boolean,
    error: String?,
    onMonthChange: (String) -> Unit,
    onRetry: () -> Unit,
    onDateClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasLoaded: Boolean = cards.isNotEmpty(),
    showRefreshIndicator: Boolean = true,
) {
    val cardsByDate = remember(cards) { cards.associateBy { it.period.take(10).replace('.', '-') } }
    val todayDate =
        currentCollectionTodayDate()
    val todayMonth = todayDate.take(7)
    val previousMonth =
        remember(month) {
            shiftCollectionMonth(
                month,
                -1,
            )
        }
    val nextMonth =
        remember(month, todayMonth) {
            shiftCollectionMonth(
                month,
                1,
            )?.takeIf { it <= todayMonth }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        CalendarHeader(
            month = month,
            previousMonth = previousMonth,
            nextMonth = nextMonth,
            todayMonth = todayMonth,
            onMonthChange = onMonthChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        CalendarDayOfWeek()
        key(month, todayMonth) {
            val initialPage = collectionMonthIndex(month)
            val pagerState = rememberPagerState(initialPage = initialPage) { collectionMonthIndex(todayMonth) + 1 }
            val latestOnMonthChange = rememberUpdatedState(onMonthChange)
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    if (page != initialPage) {
                        latestOnMonthChange.value(collectionMonthFromIndex(page))
                    }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val pageMonth = collectionMonthFromIndex(page)
                val monthDays = remember(pageMonth) { collectionMonthDays(pageMonth) }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                            .border(width = 1.dp, color = AppColors.Gray50, shape = RoundedCornerShape(4.dp))
                            .background(Color.White, shape = RoundedCornerShape(4.dp)),
                ) {
                    repeat(CALENDAR_GRID_WEEKS) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(CALENDAR_WEEK_DAYS) dayCell@{ weekday ->
                                val day = monthDays.getOrNull(week * CALENDAR_WEEK_DAYS + weekday)
                                if (day == null) {
                                    Spacer(Modifier.weight(1f).aspectRatio(CALENDAR_CELL_ASPECT_RATIO))
                                    return@dayCell
                                }
                                val card = if (pageMonth == month) day.date?.let(cardsByDate::get) else null
                                CalendarDayCell(
                                    day = day,
                                    card = card,
                                    isToday = day.date == todayDate,
                                    todayDate = todayDate,
                                    hasLoaded = hasLoaded && pageMonth == month,
                                    onClick = { day.date?.let(onDateClick) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (error != null && !hasLoaded && !isLoading) {
            CollectionRetryState(
                message = error,
                onRetry = onRetry,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (error != null && hasLoaded) {
                TextButton(onClick = onRetry) { Text("달력 다시 불러오기", color = AppColors.RecordSheetRecording) }
            }
            if (isLoading && showRefreshIndicator) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                if (hasLoaded) Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    month: String,
    previousMonth: String?,
    nextMonth: String?,
    todayMonth: String,
    onMonthChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = "이전 달",
                tint = if (previousMonth == null) AppColors.TextSecondary else AppColors.TextPrimary,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable(enabled = previousMonth != null) {
                            previousMonth?.let(onMonthChange)
                        }.padding(horizontal = 12.dp),
            )
            Text(
                "${month.take(4)}년 ${month.takeLast(2).toInt()}월",
                color = AppColors.GroupInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_forward),
                contentDescription = "다음 달",
                tint = if (nextMonth == null) AppColors.TextSecondary else AppColors.TextPrimary,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable(enabled = nextMonth != null) {
                            nextMonth?.let(onMonthChange)
                        }.padding(horizontal = 12.dp),
            )
        }
        Text(
            "오늘",
            modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.LightDivider, RoundedCornerShape(10.dp))
                    .clickable { onMonthChange(todayMonth) }
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
        )
    }
}

private val SaturdayColor = Color(0xFF2563EB)

@Composable
private fun CalendarDayOfWeek() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { index, day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 6.dp),
                color =
                    when (index) {
                        0 -> AppColors.RecordSheetRecording
                        6 -> SaturdayColor
                        else -> AppColors.TextSecondary
                    },
                textAlign = TextAlign.End,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CollectionCalendarDay,
    card: CollectionCardUiModel?,
    isToday: Boolean,
    todayDate: String,
    hasLoaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFutureDay = day.date?.let { it > todayDate } ?: false
    val baseDayColor =
        when (day.date?.let(::collectionWeekday)) {
            0 -> AppColors.RecordSheetRecording
            6 -> SaturdayColor
            else -> AppColors.TextPrimary
        }
    val dayColor =
        when {
            !day.isCurrentMonth -> AppColors.Gray200
            isFutureDay -> baseDayColor.copy(alpha = baseDayColor.alpha * 0.92f)
            else -> baseDayColor
        }
    Box(
        modifier
            .aspectRatio(CALENDAR_CELL_ASPECT_RATIO)
            .clickable(
                enabled = day.isCurrentMonth && day.date != null && !isFutureDay && hasLoaded,
                role = Role.Button,
                onClick = onClick,
            ).semantics(mergeDescendants = true) {
                contentDescription =
                    buildString {
                        append(day.date ?: day.dayOfMonth)
                        if (card != null) append(" 발급 카드")
                    }
                if (day.isCurrentMonth && day.date != null && !isFutureDay && hasLoaded) role = Role.Button
            },
    ) {
        if (card != null) {
            CalendarCard(
                card = card,
                dayOfMonth = day.dayOfMonth,
                isToday = isToday,
                modifier = Modifier.fillMaxSize().padding(1.dp),
            )
        }
        val badgeShape = RoundedCornerShape(4.dp)
        Text(
            day.dayOfMonth.toString(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .then(
                        if (isToday) {
                            Modifier
                                .background(AppColors.Border, badgeShape)
                                .padding(bottom = 1.5.dp)
                                .background(AppColors.Primary, badgeShape)
                                .border(1.dp, AppColors.Border, badgeShape)
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 4.dp, vertical = 2.dp),
            color = dayColor,
            fontSize = 9.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            lineHeight = 9.sp,
        )
    }
}

@Composable
internal fun CollectionRetryState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "다시 시도",
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = AppColors.TextSecondary, textAlign = TextAlign.Center, fontSize = 13.sp)
        TextButton(onClick = onRetry) { Text(actionLabel, color = AppColors.GroupInk) }
    }
}

@Composable
private fun CalendarSectionPreviewContent(
    hasLoaded: Boolean,
    isLoading: Boolean = false,
    error: String? = null,
    showCard: Boolean = hasLoaded,
) {
    CollectionPreviewTheme {
        val cards =
            if (showCard) {
                listOf(
                    CollectionCardUiModel(
                        id = "preview-card",
                        period = "2026.10.04",
                        title = "오늘도히유",
                        emotion = EmotionType.EXHAUSTED,
                        pressLabel = "프레스",
                        pressCount = 128,
                        stampLabel = "스탬프",
                        stampCount = 4,
                        characterImageUrl = Res.getUri("drawable/tmp_emotionimg.png"),
                        textStampImageUrl = Res.getUri("drawable/tmp_text_stamp.png"),
                        roundStampImageUrl = Res.getUri("drawable/tmp_round_stamp.png"),
                    ),
                )
            } else {
                emptyList()
            }
        CalendarSection(
            month = "2026-10",
            cards = cards,
            isLoading = isLoading,
            error = error,
            onMonthChange = {},
            onRetry = {},
            onDateClick = {},
            hasLoaded = hasLoaded,
        )
    }
}

@Preview(name = "달력 · 초기 로딩", widthDp = 402, showBackground = true)
@Composable
private fun CalendarInitialLoadingPreview() {
    CalendarSectionPreviewContent(hasLoaded = false, isLoading = true)
}

@Preview(name = "달력 · 초기 조회 실패", widthDp = 402, showBackground = true)
@Composable
private fun CalendarInitialFailurePreview() {
    CalendarSectionPreviewContent(hasLoaded = false, error = "이번 달 카드를 불러오지 못했어요.")
}

@Preview(name = "달력 · 조회 성공", widthDp = 402, showBackground = true)
@Composable
private fun CalendarSuccessPreview() {
    CalendarSectionPreviewContent(hasLoaded = true)
}

@Preview(name = "달력 · 빈 달", widthDp = 402, showBackground = true)
@Composable
private fun CalendarEmptyPreview() {
    CalendarSectionPreviewContent(hasLoaded = true, showCard = false)
}

@Preview(name = "달력 · 새로고침 중", widthDp = 402, showBackground = true)
@Composable
private fun CalendarRefreshingPreview() {
    CalendarSectionPreviewContent(hasLoaded = true, isLoading = true)
}

@Preview(name = "달력 · 새로고침 실패", widthDp = 402, showBackground = true)
@Composable
private fun CalendarRefreshFailurePreview() {
    CalendarSectionPreviewContent(hasLoaded = true, error = "달력을 새로고침하지 못했어요.")
}
