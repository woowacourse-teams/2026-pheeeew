package com.pheeeew.feature.screens.map.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val minimumFilterDate = FilterDate(2026, 9, 1)

private fun lastSelectableDay(
    year: Int,
    month: Int,
    today: FilterDate,
): Int = if (year == today.year && month == today.month) today.day else daysInMonth(year, month)

@Composable
internal fun FilterDateContent(
    initial: FilterDate?,
    onConfirm: (FilterDate) -> Unit,
) {
    val today = remember { todayForFilter() }
    var date by remember(initial) { mutableStateOf((initial ?: today).coerceIn(minimumFilterDate, today)) }
    val years = (minimumFilterDate.year..today.year).toList()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
                .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.Gray50),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateWheel(years, date.year, "년", Modifier.weight(1.3f)) {
                val firstMonth = if (it == minimumFilterDate.year) minimumFilterDate.month else 1
                val lastMonth = if (it == today.year) today.month else 12
                val month = date.month.coerceIn(firstMonth, lastMonth)
                val day = date.day.coerceAtMost(lastSelectableDay(it, month, today))
                date = FilterDate(it, month, day)
            }
            val firstMonth = if (date.year == minimumFilterDate.year) minimumFilterDate.month else 1
            val lastMonth = if (date.year == today.year) today.month else 12
            DateWheel((firstMonth..lastMonth).toList(), date.month, "월", Modifier.weight(1f)) {
                date =
                    date.copy(month = it, day = date.day.coerceAtMost(lastSelectableDay(date.year, it, today)))
            }
            val lastDay = lastSelectableDay(date.year, date.month, today)
            DateWheel((1..lastDay).toList(), date.day, "일", Modifier.weight(1f)) {
                date =
                    date.copy(day = it)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppColors.Surface, AppColors.Surface.copy(alpha = 0f)),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppColors.Surface.copy(alpha = 0f), AppColors.Surface),
                    ),
                ),
        )
    }
    FilterActionButton("확인") { onConfirm(date.coerceIn(minimumFilterDate, today)) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun DateWheel(
    values: List<Int>,
    selected: Int,
    suffix: String,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    LaunchedEffect(values.first(), values.last()) {
        listState.scrollToItem(selectedIndex)
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> values.getOrNull(index)?.let(onSelect) }
    }
    Box(modifier.height(200.dp), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(2) { Spacer(Modifier.height(40.dp)) }
            itemsIndexed(values) { index, value ->
                val isSelected = index == listState.firstVisibleItemIndex
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clickable {
                        scope.launch { listState.animateScrollToItem(index) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$value$suffix",
                        fontSize = 16.sp,
                        color = if (isSelected) AppColors.GroupInk else Color.LightGray,
                    )
                }
            }
            items(2) { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Preview(name = "필터 날짜 선택", widthDp = 402, heightDp = 560, showBackground = true)
@Composable
private fun FilterDateContentPreview() {
    AppTheme {
        Column(Modifier.background(AppColors.Surface).padding(horizontal = 24.dp)) {
            FilterDateContent(FilterDate(2026, 9, 1)) {}
        }
    }
}
