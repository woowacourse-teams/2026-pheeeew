package com.pheeeew.feature.screens.map.nearby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

internal class NearbySheetNestedScrollConnection(
    private val middleOffset: Float,
    private val collapseThreshold: Float,
    private val isExpanded: () -> Boolean,
    private val onDrag: (Float) -> Unit,
    private val onFinish: (collapse: Boolean) -> Unit,
) : NestedScrollConnection {
    private var pullingSheet = false
    private var draggedOffset = 0f

    private fun drag(amount: Float): Offset {
        val previous = draggedOffset
        draggedOffset = (previous + amount).coerceIn(0f, middleOffset)
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
        if (source != NestedScrollSource.UserInput || !isExpanded() || available.y <= 0f) return Offset.Zero
        if (!pullingSheet) {
            pullingSheet = true
            draggedOffset = 0f
        }
        return drag(available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!pullingSheet) return Velocity.Zero
        // A pull from the list stops at half height, even after a long or fast gesture.
        onFinish(draggedOffset >= collapseThreshold)
        pullingSheet = false
        draggedOffset = 0f
        return available
    }
}
