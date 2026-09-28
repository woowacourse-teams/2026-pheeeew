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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Non-modal sheet: uses the legacy SighListSheet middle ratio and drag thresholds. */
@Composable
internal fun NearbySheetLayout(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(260)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                val density = LocalDensity.current
                val availableHeightPx = constraints.maxHeight.toFloat()
                val middleOffset = availableHeightPx * 0.55f
                var expanded by remember { mutableStateOf(false) }
                var dragging by remember { mutableStateOf(false) }
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
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { (availableHeightPx - offset).coerceAtLeast(0f).toDp() }),
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
                        content()
                    }
                }
            }
        }
    }
}
