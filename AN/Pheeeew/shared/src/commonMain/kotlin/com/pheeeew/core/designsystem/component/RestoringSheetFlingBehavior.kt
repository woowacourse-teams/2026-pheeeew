package com.pheeeew.core.designsystem.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue

/** Requests dismissal at release, while returning directly from the dragged offset. */
@OptIn(ExperimentalMaterial3Api::class)
internal class RestoringSheetFlingBehavior(
    private val state: AnchoredDraggableState<SheetValue>,
    private val positionalThreshold: Float,
    private val velocityThreshold: Float,
    private val animationSpec: AnimationSpec<Float>,
    private val onDismissRequest: () -> Unit,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val offset = state.requireOffset()
        val hiddenOffset = state.anchors.positionOf(SheetValue.Hidden)
        // Match the sheet's directional position/velocity thresholds without flinging to Hidden.
        val dismiss =
            offset > 0f &&
                when {
                    initialVelocity >= velocityThreshold -> true
                    initialVelocity <= -velocityThreshold -> false
                    initialVelocity > 0f -> offset >= positionalThreshold
                    initialVelocity < 0f -> hiddenOffset - offset < positionalThreshold
                    else -> offset >= hiddenOffset / 2f
                }
        if (dismiss) onDismissRequest()
        // performFling runs inside anchoredDraggable's existing anchoredDrag mutation.
        // Starting another state.animateTo here would cancel that mutation (and this coroutine).
        val expandedOffset = state.anchors.positionOf(SheetValue.Expanded)
        animate(
            initialValue = offset,
            targetValue = expandedOffset,
            animationSpec = animationSpec,
        ) { value, _ ->
            scrollBy(value - state.requireOffset())
        }
        scrollBy(expandedOffset - state.requireOffset())
        return 0f
    }
}
