package com.pheeeew.feature.screens.map.nearby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

internal class NearbySheetNestedScrollConnection(
    private val middleOffset: Float,
    private val hiddenOffset: Float,
    private val collapseThreshold: Float,
    private val dismissThreshold: Float,
    private val isExpanded: () -> Boolean,
    private val onDrag: (Float) -> Unit,
    private val onFinish: (collapse: Boolean) -> Unit,
    private val onDismiss: () -> Unit,
) : NestedScrollConnection {
    private var pullingSheet = false
    private var startedExpanded = true
    private var draggedOffset = 0f

    private val startOffset: Float
        get() = if (startedExpanded) 0f else middleOffset

    private fun drag(amount: Float): Offset {
        val previous = draggedOffset
        val endOffset = if (startedExpanded) middleOffset else hiddenOffset
        draggedOffset = (previous + amount).coerceIn(startOffset, endOffset)
        onDrag(draggedOffset)
        return Offset(0f, draggedOffset - previous)
    }

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || !pullingSheet) return Offset.Zero
        return drag(available.y)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // Only the downward distance left over at the list's top moves the sheet.
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        if (!pullingSheet) {
            pullingSheet = true
            startedExpanded = isExpanded()
            draggedOffset = startOffset
        }
        return drag(available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!pullingSheet) return Velocity.Zero
        // Each pull moves down one step: full to half, or half to hidden.
        if (!startedExpanded && draggedOffset - startOffset >= dismissThreshold) {
            onDismiss()
        } else {
            onFinish(startedExpanded && draggedOffset >= collapseThreshold)
        }
        pullingSheet = false
        draggedOffset = 0f
        return available
    }
}
