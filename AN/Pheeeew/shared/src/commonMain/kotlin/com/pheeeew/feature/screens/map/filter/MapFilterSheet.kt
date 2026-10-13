package com.pheeeew.feature.screens.map.filter

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.AppNavigableBottomSheet
import com.pheeeew.core.designsystem.component.RaisedCardButton
import com.pheeeew.core.designsystem.component.SheetDragHandle
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.map.record.group.GroupSelectionStamp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_forward

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapFilterSheet(
    state: MapFilterUiModel,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onPeriod: (FilterPeriod) -> Unit,
    onContent: (FilterContent) -> Unit,
    onScope: (FilterScope) -> Unit,
    onOpenDate: (Boolean) -> Unit,
    onConfirmDate: (FilterDate) -> Unit,
    onOpenGroups: () -> Unit,
    onToggleGroup: (String) -> Unit,
    onClosePage: () -> Unit,
    onApply: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val contentScroll =
        if (state.page == FilterPage.Filters) Modifier.verticalScroll(rememberScrollState()) else Modifier
    AppNavigableBottomSheet(
        onDismissRequest = onDismiss,
        onBackOrOutsideClick = {
            if (state.page == FilterPage.Filters) {
                coroutineScope.launch {
                    sheetState.hide()
                    if (!sheetState.isVisible) onDismiss()
                }
            } else {
                onClosePage()
            }
        },
        outsideContentDescription = if (state.page == FilterPage.Filters) "필터 닫기" else "필터로 돌아가기",
        sheetState = sheetState,
        shape = AppShapes.BottomSheet,
        containerColor = AppColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        dragHandle = { SheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(contentScroll)
                .padding(horizontal = 24.dp),
        ) {
            when (state.page) {
                FilterPage.Filters -> {
                    MapFilterContent(state, onReset, onPeriod, onContent, onScope, onOpenDate, onOpenGroups, onApply)
                }

                FilterPage.StartDate, FilterPage.EndDate -> {
                    FilterDateContent(
                        initial = if (state.page == FilterPage.StartDate) state.startDate else state.endDate,
                        onConfirm = onConfirmDate,
                    )
                }

                FilterPage.Groups -> {
                    FilterGroupsContent(state, onToggleGroup, onClosePage)
                }
            }
        }
    }
}

@Composable
internal fun MapFilterContent(
    state: MapFilterUiModel,
    onReset: () -> Unit,
    onPeriod: (FilterPeriod) -> Unit,
    onContent: (FilterContent) -> Unit,
    onScope: (FilterScope) -> Unit,
    onOpenDate: (Boolean) -> Unit,
    onOpenGroups: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "필터", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        // AppNavigableBottomSheet disables ripples; restore the default for this action only.
        CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration()) {
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !state.applying, role = Role.Button, onClick = onReset)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "초기화",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.BorderLight))
    Spacer(Modifier.height(20.dp))
    FilterLabel(label = "조회 기간")
    Spacer(Modifier.height(8.dp))
    FilterChoices(
        labels = listOf("2주", "1개월", "전체", "직접 설정"),
        selected = state.period.ordinal,
        onSelect = { onPeriod(FilterPeriod.entries[it]) },
        enabled = !state.applying,
    )
    if (state.period == FilterPeriod.Custom) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DateField(state.startDate, "시작일", Modifier.weight(1f), enabled = !state.applying) { onOpenDate(true) }
            Text("~", color = AppColors.TextSecondary)
            DateField(state.endDate, "종료일", Modifier.weight(1f), enabled = !state.applying) { onOpenDate(false) }
        }
    }
    Spacer(Modifier.height(20.dp))
    FilterLabel(label = "내용")
    Spacer(Modifier.height(8.dp))
    FilterChoices(
        listOf("전체", "메모만", "녹음만"),
        state.content.ordinal,
        enabled = !state.applying,
    ) { onContent(FilterContent.entries[it]) }
    Spacer(Modifier.height(20.dp))
    FilterLabel(label = "조회 범위")
    Spacer(Modifier.height(8.dp))
    FilterChoices(
        listOf("전체", "내 기록만", "그룹 선택"),
        state.scope.ordinal,
        enabled = !state.applying,
    ) { onScope(FilterScope.entries[it]) }
    if (state.scope == FilterScope.Groups) {
        SelectedGroups(state, enabled = !state.applying, onOpenGroups = onOpenGroups)
    }
    state.applyError?.let { error ->
        Text(error, modifier = Modifier.padding(top = 12.dp), color = AppColors.Error, fontSize = 13.sp)
    }
    Spacer(Modifier.height(64.dp))
    if (state.applying) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("조회 중…", modifier = Modifier.padding(start = 10.dp), fontSize = 16.sp)
        }
    } else {
        FilterActionButton(label = "완료", onClick = onApply)
    }
}

@Composable
private fun FilterLabel(label: String) {
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.GroupInk)
}

@Composable
private fun FilterChoices(
    labels: List<String>,
    selected: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val selectionPosition by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "FilterChoicePosition",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Gray50)
            .padding(4.dp)
            .drawBehind {
                if (labels.isNotEmpty()) {
                    val itemWidth = size.width / labels.size
                    drawRoundRect(
                        color = AppColors.Surface,
                        topLeft = Offset(itemWidth * selectionPosition, 0f),
                        size = Size(itemWidth, size.height),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
            },
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val textColor by animateColorAsState(
                targetValue = if (active) AppColors.TextPrimary else AppColors.TextSecondary,
                animationSpec = tween(150),
                label = "FilterChoiceTextColor",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp)
                    .clickable(enabled = enabled, interactionSource = null, indication = null) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DateField(
    date: FilterDate?,
    placeholder: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val textColor = if (date == null) AppColors.TextSecondary else AppColors.GroupInk
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Gray50)
            .padding(vertical = 10.dp)
            .clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date?.toString() ?: placeholder,
            fontSize = 14.sp,
            color = textColor,
        )
    }
}

@Composable
private fun SelectedGroups(
    state: MapFilterUiModel,
    enabled: Boolean = true,
    onOpenGroups: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = 56.dp,
            ).clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onOpenGroups)
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val selected =
            remember(state.groups, state.selectedGroupIds) {
                state.groups.filter { it.id in state.selectedGroupIds }
            }
        if (selected.isEmpty()) {
            Text("그룹을 선택해주세요", color = AppColors.TextSecondary, fontSize = 14.sp)
        } else {
            selected.take(5).forEach { group ->
                GroupSelectionStamp(
                    group.stamp,
                    size = 32.dp,
                    modifier = Modifier.padding(start = 6.dp),
                    emptyLabel = group.name,
                )
            }
            val overflow = selected.size - 5
            if (overflow > 0) {
                Text("+$overflow", modifier = Modifier.padding(start = 6.dp), fontSize = 12.sp)
            }
        }
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_forward),
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(12.dp),
            tint = AppColors.TextSecondary,
        )
    }
}

@Composable
internal fun FilterActionButton(
    label: String,
    onClick: () -> Unit,
) {
    RaisedCardButton(
        backgroundColor = AppColors.Primary,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(
            label,
            modifier = Modifier.align(Alignment.Center),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "지도 필터", widthDp = 402, heightDp = 760, showBackground = true)
@Composable
private fun MapFilterContentPreview() {
    AppTheme {
        Column(Modifier.background(AppColors.Surface).padding(horizontal = 24.dp)) {
            MapFilterContent(
                MapFilterUiModel(
                    visible = true,
                    period = FilterPeriod.Custom,
                    scope = FilterScope.Groups,
                ),
                onReset = {},
                onPeriod = {},
                onContent = {},
                onScope = {},
                onOpenDate = {},
                onOpenGroups = {},
                onApply = {},
            )
        }
    }
}
