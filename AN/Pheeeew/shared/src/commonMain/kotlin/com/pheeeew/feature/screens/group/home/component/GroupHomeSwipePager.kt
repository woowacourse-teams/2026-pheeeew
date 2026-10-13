// Lucian UX/UI Prompt #048 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#048
package com.pheeeew.feature.screens.group.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.home.GroupHomeTab
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_explore_title
import pheeeew.shared.generated.resources.group_home_my_groups
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GroupHomeSwipePager(
    selectedTab: GroupHomeTab,
    onTabSelected: (GroupHomeTab) -> Unit,
    modifier: Modifier = Modifier,
    isSearchOpen: Boolean = false,
    headerContent: @Composable (@Composable () -> Unit) -> Unit = { tabs ->
        Box(Modifier.padding(horizontal = 24.dp)) { tabs() }
    },
    pageContent: @Composable (GroupHomeTab) -> Unit,
) {
    val tabs = remember { GroupHomeTab.entries }
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, isScrolling) ->
                if (!isScrolling) currentOnTabSelected(tabs[page])
            }
    }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        headerContent {
            GroupHomeAnimatedTabs(
                tabs = tabs,
                pagerState = pagerState,
                onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            )
        }
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSearchOpen,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { page -> tabs[page].name },
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                pageContent(tabs[page])
            }
        }
    }
}

@Composable
private fun GroupHomeAnimatedTabs(
    tabs: List<GroupHomeTab>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
) {
    val labels =
        tabs.map { tab ->
            stringResource(
                if (tab == GroupHomeTab.MyGroups) {
                    Res.string.group_home_my_groups
                } else {
                    Res.string.group_explore_title
                },
            )
        }
    val focusRequesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    val pagePosition by remember(pagerState) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, tabs.lastIndex.toFloat())
        }
    }
    val selectedIndex = pagePosition.roundToInt()

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val tabWidth = maxWidth / tabs.size
        val lowerPage = floor(pagePosition).toInt().coerceIn(0, tabs.lastIndex)
        val fraction = if (lowerPage == tabs.lastIndex) 0f else pagePosition - lowerPage
        val stretch = 24.dp * sin(fraction * PI).toFloat()
        val indicatorWidth = tabWidth + stretch
        val indicatorOffset = tabWidth * pagePosition - stretch / 2
        val indicatorFraction = indicatorWidth / maxWidth

        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                verticalAlignment = Alignment.Bottom,
            ) {
                tabs.forEachIndexed { index, _ ->
                    val selected = index == selectedIndex
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .focusRequester(focusRequesters[index])
                                .onKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyUp) {
                                        return@onKeyEvent false
                                    }
                                    val target =
                                        when (event.key) {
                                            Key.DirectionRight -> (index + 1).takeIf { it <= tabs.lastIndex }
                                            Key.DirectionLeft -> (index - 1).takeIf { it >= 0 }
                                            else -> null
                                        } ?: return@onKeyEvent false
                                    focusRequesters[target].requestFocus()
                                    onTabClick(target)
                                    true
                                }.selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { onTabClick(index) },
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = labels[index],
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = if (selected) AppColors.GroupInk else AppColors.RankingSecondaryContent,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE5E7E4)),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = indicatorOffset)
                        .fillMaxWidth(indicatorFraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(AppColors.GroupInk),
            )
        }
    }
}
