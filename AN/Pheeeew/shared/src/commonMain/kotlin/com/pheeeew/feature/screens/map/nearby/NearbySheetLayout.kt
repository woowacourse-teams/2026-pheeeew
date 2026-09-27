package com.pheeeew.feature.screens.map.nearby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** Non-modal sheet: uses the legacy SighListSheet middle ratio and drag thresholds. */
@Composable
internal fun NearbySheetLayout(
    visible: Boolean,
    scroll: LazyListState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(bottomPadding: Dp, nestedScroll: NestedScrollConnection) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(260)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val availableHeightPx = constraints.maxHeight.toFloat()
                val middleOffset = availableHeightPx * 0.55f
                var expanded by remember { mutableStateOf(false) }
                var dragging by remember { mutableStateOf(false) }
                var listDragging by remember { mutableStateOf(false) }
                var draggedOffset by remember { mutableFloatStateOf(0f) }
                var totalDrag by remember { mutableFloatStateOf(0f) }
                val offset by animateFloatAsState(
                    if (dragging) {
                        draggedOffset
                    } else if (expanded) {
                        0f
                    } else {
                        middleOffset
                    },
                    if (dragging) snap() else tween(260),
                    label = "nearby-sheet-offset",
                )
                val expandThreshold = with(density) { 48.dp.toPx() }
                val collapseThreshold = with(density) { 28.dp.toPx() }
                val dismissThreshold = with(density) { 72.dp.toPx() }
                val expandedDismissThreshold = with(density) { 180.dp.toPx() }.coerceAtMost(availableHeightPx * 0.34f)
                val finishDrag =
                    rememberUpdatedState {
                        val dismiss =
                            if (expanded) {
                                if (totalDrag >= collapseThreshold) expanded = false
                                totalDrag >= expandedDismissThreshold
                            } else {
                                if (totalDrag <= -expandThreshold) expanded = true
                                totalDrag >= dismissThreshold
                            }
                        if (dismiss) {
                            onDismiss()
                        } else {
                            dragging = false
                            totalDrag = 0f
                        }
                    }
                val nestedScroll =
                    remember(scroll, availableHeightPx) {
                        object : NestedScrollConnection {
                            override fun onPreScroll(
                                available: Offset,
                                source: NestedScrollSource,
                            ): Offset {
                                if (source != NestedScrollSource.UserInput) return Offset.Zero
                                val atTop =
                                    scroll.firstVisibleItemIndex == 0 && scroll.firstVisibleItemScrollOffset == 0
                                if (!listDragging && !(expanded && atTop && available.y > 0f)) return Offset.Zero
                                if (!listDragging) {
                                    listDragging = true
                                    dragging = true
                                    draggedOffset = 0f
                                    totalDrag = 0f
                                }
                                val next = (draggedOffset + available.y).coerceIn(0f, availableHeightPx)
                                val consumed = next - draggedOffset
                                draggedOffset = next
                                totalDrag += consumed
                                return Offset(0f, consumed)
                            }

                            override suspend fun onPreFling(available: Velocity): Velocity {
                                if (!listDragging) return Velocity.Zero
                                listDragging = false
                                finishDrag.value()
                                return Velocity(0f, available.y)
                            }
                        }
                    }
                Surface(
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationY = offset },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color(0xFFFFFCF6),
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .then(if (expanded) Modifier.statusBarsPadding() else Modifier),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(28.dp).pointerInput(availableHeightPx) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        listDragging = false
                                        dragging = true
                                        draggedOffset = offset
                                        totalDrag = 0f
                                    },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        totalDrag += amount
                                        draggedOffset = (draggedOffset + amount).coerceIn(0f, availableHeightPx)
                                    },
                                    onDragEnd = { finishDrag.value() },
                                    onDragCancel = {
                                        dragging = false
                                        totalDrag = 0f
                                    },
                                )
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(36.dp, 4.dp).background(Color(0xFFD9D8D1), RoundedCornerShape(2.dp)))
                        }
                        content(16.dp + with(density) { offset.toDp() }, nestedScroll)
                    }
                }
            }
        }
    }
}
