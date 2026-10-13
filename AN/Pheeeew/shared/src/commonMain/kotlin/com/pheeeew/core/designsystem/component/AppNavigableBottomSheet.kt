package com.pheeeew.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppFontScale
import kotlin.math.roundToInt

/** Outside clicks and back navigate within the sheet; a swipe dismisses the entire sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppNavigableBottomSheet(
    onDismissRequest: () -> Unit,
    onBackOrOutsideClick: () -> Unit,
    outsideContentDescription: String,
    sheetState: SheetState,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onBackOrOutsideClick,
        properties = navigableBottomSheetDialogProperties(),
    ) {
        val handleDragState = remember { AnchoredDraggableState(SheetValue.Expanded) }
        val handleFlingBehavior = AnchoredDraggableDefaults.flingBehavior(handleDragState)
        val latestDismissRequest by rememberUpdatedState(onDismissRequest)
        LaunchedEffect(handleDragState) {
            snapshotFlow { handleDragState.settledValue }.collect { value ->
                if (value == SheetValue.Hidden) latestDismissRequest()
            }
        }
        val sheetOffsetInsets = remember(sheetState) { SheetOffsetWindowInsets(sheetState) }
        val scrimAlpha by animateFloatAsState(
            targetValue = if (sheetState.targetValue == SheetValue.Hidden) 0f else 1f,
            label = "NavigableSheetScrim",
        )
        Box(Modifier.fillMaxSize().imePadding().semantics { isTraversalGroup = true }) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * scrimAlpha))
                    .clickable(interactionSource = null, indication = null, onClick = onBackOrOutsideClick)
                    .semantics { contentDescription = outsideContentDescription },
            )
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                BottomSheet(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset {
                                IntOffset(0, handleDragState.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0)
                            }.onSizeChanged { size ->
                                if (size.height > 0) {
                                    handleDragState.updateAnchors(
                                        DraggableAnchors {
                                            SheetValue.Expanded at 0f
                                            SheetValue.Hidden at size.height.toFloat()
                                        },
                                    )
                                }
                            }.consumeWindowInsets(sheetOffsetInsets),
                    state = sheetState,
                    onDismissRequest = onDismissRequest,
                    gesturesEnabled = false,
                    backHandlerEnabled = false,
                    contentWindowInsets = { BottomSheetDefaults.modalWindowInsets },
                    shape = shape,
                    containerColor = containerColor,
                    contentColor = contentColorFor(containerColor),
                    dragHandle =
                        dragHandle?.let { handle ->
                            {
                                Box(
                                    Modifier.fillMaxWidth().anchoredDraggable(
                                        state = handleDragState,
                                        orientation = Orientation.Vertical,
                                        enabled =
                                            sheetState.currentValue == SheetValue.Expanded &&
                                                !sheetState.isAnimationRunning,
                                        flingBehavior = handleFlingBehavior,
                                    ),
                                ) {
                                    AppFontScale(handle)
                                }
                            }
                        },
                ) {
                    AppFontScale { content() }
                }
            }
        }
        if (sheetState.hasExpandedState) {
            LaunchedEffect(sheetState) { sheetState.show() }
        }
    }
}

/** The sheet's offset already separates its content from the top system bars. */
@OptIn(ExperimentalMaterial3Api::class)
private class SheetOffsetWindowInsets(
    private val sheetState: SheetState,
) : WindowInsets {
    override fun getTop(density: Density): Int =
        runCatching { sheetState.requireOffset().toInt().coerceAtLeast(0) }.getOrDefault(0)

    override fun getLeft(
        density: Density,
        layoutDirection: LayoutDirection,
    ): Int = 0

    override fun getRight(
        density: Density,
        layoutDirection: LayoutDirection,
    ): Int = 0

    override fun getBottom(density: Density): Int = 0
}

internal expect fun navigableBottomSheetDialogProperties(): DialogProperties
