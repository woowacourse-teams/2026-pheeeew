package com.pheeeew.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.input.dismissNativeTextKeyboard
import com.pheeeew.core.designsystem.theme.AppFontScale
import com.pheeeew.core.navigation.FlowBackHandler
import kotlin.math.roundToInt

/** Outside clicks and back navigate within the sheet; a swipe dismisses the entire sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppNavigableBottomSheet(
    onDismissRequest: () -> Unit,
    onBackOrOutsideClick: () -> Unit,
    outsideContentDescription: String,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    dismissKeyboardBeforeNavigation: Boolean = false,
    dragFromContent: Boolean = false,
    restoreOnSwipeDismissRequest: Boolean = false,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    // The Dialog owns the focus manager and IME insets used for navigation.
    var handleDialogBack by remember { mutableStateOf(onBackOrOutsideClick) }
    Dialog(
        onDismissRequest = { handleDialogBack() },
        properties = navigableBottomSheetDialogProperties(keyboardAware = dismissKeyboardBeforeNavigation),
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val currentKeyboardVisible by rememberUpdatedState(keyboardVisible)
        var gestureStartedWithKeyboard by remember { mutableStateOf(false) }
        val navigate: (() -> Unit, Boolean) -> Unit = { action, startedWithKeyboard ->
            if (dismissKeyboardBeforeNavigation && (currentKeyboardVisible || startedWithKeyboard)) {
                dismissNativeTextKeyboard()
                focusManager.clearFocus()
                keyboardController?.hide()
            } else {
                action()
            }
        }
        val currentNavigate by rememberUpdatedState(navigate)
        val currentBack by rememberUpdatedState(onBackOrOutsideClick)
        val navigateBack = remember { { currentNavigate(currentBack, false) } }
        SideEffect { handleDialogBack = navigateBack }
        FlowBackHandler(enabled = true, onBack = navigateBack)
        val handleDragState = remember { AnchoredDraggableState(SheetValue.Expanded) }
        val defaultFlingBehavior = AnchoredDraggableDefaults.flingBehavior(handleDragState)
        val latestDismissRequest by rememberUpdatedState(onDismissRequest)
        val requestSwipeDismiss by rememberUpdatedState<() -> Unit> {
            currentNavigate(latestDismissRequest, gestureStartedWithKeyboard)
        }
        val density = LocalDensity.current
        val returnAnimation = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val handleFlingBehavior =
            if (restoreOnSwipeDismissRequest) {
                remember(handleDragState, density, returnAnimation) {
                    RestoringSheetFlingBehavior(
                        state = handleDragState,
                        positionalThreshold = with(density) { 56.dp.toPx() },
                        velocityThreshold = with(density) { 125.dp.toPx() },
                        animationSpec = returnAnimation,
                        onDismissRequest = { requestSwipeDismiss() },
                    )
                }
            } else {
                defaultFlingBehavior
            }
        LaunchedEffect(handleDragState, restoreOnSwipeDismissRequest) {
            snapshotFlow { handleDragState.settledValue }.collect { value ->
                if (value == SheetValue.Hidden && !restoreOnSwipeDismissRequest) {
                    // A dismissal request may open a confirmation instead of removing this sheet.
                    // Restore the drag state before dispatch so cancel leaves a usable sheet.
                    handleDragState.snapTo(SheetValue.Expanded)
                    currentNavigate(latestDismissRequest, gestureStartedWithKeyboard)
                }
            }
        }
        val sheetOffsetInsets = remember(sheetState) { SheetOffsetWindowInsets(sheetState) }
        val scrimAlpha by animateFloatAsState(
            targetValue = if (sheetState.targetValue == SheetValue.Hidden) 0f else 1f,
            label = "NavigableSheetScrim",
        )
        val dragModifier =
            Modifier.anchoredDraggable(
                state = handleDragState,
                orientation = Orientation.Vertical,
                enabled = sheetState.currentValue == SheetValue.Expanded && !sheetState.isAnimationRunning,
                flingBehavior = handleFlingBehavior,
            )
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // Capture before a child or UIKit dismisses the IME during this gesture.
                        gestureStartedWithKeyboard = currentKeyboardVisible
                    }
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * scrimAlpha))
                    .clickable(interactionSource = null, indication = null) {
                        currentNavigate(currentBack, gestureStartedWithKeyboard)
                    }.semantics { contentDescription = outsideContentDescription },
            )
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                BottomSheet(
                    modifier =
                        modifier
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
                                    Modifier
                                        .fillMaxWidth()
                                        .then(dragModifier)
                                        .semantics { contentDescription = outsideContentDescription }
                                        .clickable(interactionSource = null, indication = null) {
                                            currentNavigate(latestDismissRequest, gestureStartedWithKeyboard)
                                        },
                                ) {
                                    AppFontScale(handle)
                                }
                            }
                        },
                ) {
                    Column(if (dragFromContent) dragModifier else Modifier) {
                        AppFontScale { content() }
                    }
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

internal expect fun navigableBottomSheetDialogProperties(keyboardAware: Boolean = false): DialogProperties
