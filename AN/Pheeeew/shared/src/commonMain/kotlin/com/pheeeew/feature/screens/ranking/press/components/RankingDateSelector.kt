package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.feature.screens.ranking.press.rankingDateLabel
import com.pheeeew.feature.screens.ranking.press.rankingMonthDays
import com.pheeeew.feature.screens.ranking.press.rankingWeekday
import com.pheeeew.feature.screens.ranking.press.shiftRankingDate
import com.pheeeew.feature.screens.ranking.press.shiftRankingMonth

private val dateInk = Color(0xFF202323)
private val dateYellow = Color(0xFFFFE66B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RankingDateSelector(
    date: String,
    today: String,
    openRequest: Int = 0,
    onOpenRequestHandled: () -> Unit = {},
    onSelect: (String) -> Unit,
) {
    var showingCalendar by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        if (openRequest > 0) {
            showingCalendar = true
            onOpenRequestHandled()
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val controlHeight = (maxWidth * 0.08f).coerceIn(30.dp, 40.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .heightIn(min = controlHeight)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateArrow("‹", "이전 날짜", date > "0001-01-01", Modifier.width(controlHeight).fillMaxHeight()) {
                onSelect(shiftRankingDate(date, -1))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, dateInk, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        showingCalendar =
                            true
                    }.semantics { contentDescription = "${rankingDateLabel(date)}, 날짜 선택" },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        rankingDateLabel(date),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = dateInk,
                    )
                    Canvas(Modifier.padding(start = 8.dp).size(10.dp)) {
                        val stroke = 1.5.dp.toPx()
                        drawLine(
                            dateInk,
                            Offset(0f, size.height * 0.3f),
                            Offset(size.width / 2, size.height * 0.7f),
                            stroke,
                            StrokeCap.Round,
                        )
                        drawLine(
                            dateInk,
                            Offset(size.width / 2, size.height * 0.7f),
                            Offset(size.width, size.height * 0.3f),
                            stroke,
                            StrokeCap.Round,
                        )
                    }
                }
            }
            DateArrow("›", "다음 날짜", date < today, Modifier.width(controlHeight).fillMaxHeight()) {
                onSelect(shiftRankingDate(date, 1))
            }
            Box(
                Modifier
                    .widthIn(min = controlHeight * 1.7f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (date == today) Color(0xFFF0F1EB) else Color(0xFFFFF1A2))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled =
                            date != today,
                        role = Role.Button,
                    ) { onSelect(today) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "오늘",
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color =
                        if (date ==
                            today
                        ) {
                            Color(0xFF767D78)
                        } else {
                            dateInk
                        },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (showingCalendar) {
        ModalBottomSheet(
            onDismissRequest = { showingCalendar = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFFFAFAFA),
            contentColor = dateInk,
        ) {
            RankingCalendar(date, today) {
                onSelect(it)
                showingCalendar = false
            }
        }
    }
}

@Composable
private fun RankingCalendar(
    initial: String,
    today: String,
    onConfirm: (String) -> Unit,
) {
    var selectedDate by rememberSaveable(initial) { mutableStateOf(initial) }
    var month by rememberSaveable(initial) { mutableStateOf(initial.take(7)) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DateArrow("‹", "이전 달", month > "0001-01") { month = shiftRankingMonth(month, -1) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("${month.take(4)}년 ${month.takeLast(2).toInt()}월", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            DateArrow("›", "다음 달", month < today.take(7)) { month = shiftRankingMonth(month, 1) }
        }
        Row(Modifier.fillMaxWidth()) {
            "일월화수목금토".forEach { day ->
                Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                    Text(day.toString(), color = Color(0xFF767D78))
                }
            }
        }
        val dates = rankingMonthDays(month)
        val cells = List<String?>(rankingWeekday(dates.first())) { null } + dates
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0..6).forEach { index ->
                    val day = week.getOrNull(index)
                    val enabled = day != null && day <= today
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource =
                                    remember {
                                        MutableInteractionSource()
                                    },
                                indication = null,
                                enabled = enabled,
                                role = Role.Button,
                            ) {
                                selectedDate =
                                    day!!
                            }.semantics {
                                if (day != null) {
                                    contentDescription = rankingDateLabel(day) + if (day == today) ", 오늘" else ""
                                    selected = day == selectedDate
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(if (day == selectedDate) dateYellow else Color.Transparent, CircleShape)
                                    .then(
                                        if (day == today) {
                                            Modifier.border(1.dp, dateInk, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.takeLast(2).toInt().toString(),
                                    color = if (enabled) dateInk else Color.LightGray,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("오늘 이후 날짜는 선택할 수 없어요", fontSize = 12.sp, color = Color(0xFF767D78))
        Spacer(Modifier.height(16.dp))
        PressRankingCardSurface(
            color = dateYellow,
            shape = RoundedCornerShape(14.dp),
            onClick = { onConfirm(selectedDate) },
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${selectedDate.substring(5, 7).toInt()}월 ${selectedDate.takeLast(2).toInt()}일 조회",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DateArrow(
    text: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier.size(32.dp),
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(CircleShape)
            .clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val color = if (enabled) dateInk else Color.LightGray
            val outerX = size.width * if (text == "‹") 0.65f else 0.35f
            val innerX = size.width * if (text == "‹") 0.35f else 0.65f
            val center = Offset(innerX, size.height / 2)
            drawLine(color, Offset(outerX, size.height * 0.15f), center, 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, center, Offset(outerX, size.height * 0.85f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
