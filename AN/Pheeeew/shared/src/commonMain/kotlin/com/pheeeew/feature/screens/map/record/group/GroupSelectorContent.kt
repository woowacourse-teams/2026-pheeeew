package com.pheeeew.feature.screens.map.record.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.map.record.noRippleClickable
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.record_group_select
import pheeeew.shared.generated.resources.record_group_select_done
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private val DIAL_HEIGHT = 283.dp
private val DIAL_RADIUS = 316.dp
private val DIAL_ITEM_SIZE = 70.dp
private val DIAL_STEP = 100.dp

// The arc drops about 17% of the screen width from its apex to either edge.
private const val DIAL_BACKGROUND_RADIUS_TO_WIDTH = 0.82f

private const val DIAL_CENTER_Y = 445f
private const val DIAL_STEP_DEGREES = 18.5f
private const val DIAL_SCROLL_DURATION_MILLIS = 420
private const val DIAL_SHEET_DAMPING_RATIO = 0.912f
private const val DIAL_SHEET_STIFFNESS = 390f
private const val DIAL_SHEET_START_OFFSET_DP = 320

@Composable
fun GroupSelectorContent(
    isVisible: Boolean,
    groups: List<GroupSelectorGroupUiModel>,
    selectedGroupId: String,
    dialProgress: Float,
    onDialProgressChange: (Float) -> Unit,
    onDialProgressSettle: (Float) -> Unit,
    onSelectedGroupChange: (GroupSelectorGroupUiModel) -> Unit,
    onDismiss: () -> Unit,
    onComplete: (GroupSelectorGroupUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)
    val selectedGroup = groups.getOrNull(selectedIndex) ?: return
    var isDragging by remember { mutableStateOf(false) }
    val animatedDialProgress by animateFloatAsState(
        targetValue = dialProgress,
        animationSpec =
            if (isDragging || !isVisible) {
                snap()
            } else {
                tween(durationMillis = DIAL_SCROLL_DURATION_MILLIS, easing = FastOutSlowInEasing)
            },
    )
    val visibleDialProgress = if (isDragging) dialProgress else animatedDialProgress
    val dialProgressState by rememberUpdatedState(visibleDialProgress)
    val selectedIndexState by rememberUpdatedState(selectedIndex)
    val onDialProgressChangeState by rememberUpdatedState(onDialProgressChange)
    val onDialProgressSettleState by rememberUpdatedState(onDialProgressSettle)
    val onSelectedGroupChangeState by rememberUpdatedState(onSelectedGroupChange)
    val density = LocalDensity.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val densityScale = density.density
    val dragStepPx = with(density) { DIAL_STEP.toPx() }
    val dialRadiusPx = with(density) { DIAL_RADIUS.toPx() }
    val dialCenterYPx = with(density) { DIAL_CENTER_Y.dp.toPx() }
    val dialItemHalfSizePx = with(density) { DIAL_ITEM_SIZE.toPx() / 2f }
    val sheetStartOffsetPx = with(density) { (DIAL_SHEET_START_OFFSET_DP.dp + bottomInset).roundToPx() }
    val isPreview = LocalInspectionMode.current
    val scrimEnter = if (isPreview) EnterTransition.None else fadeIn(tween(durationMillis = 160))
    val scrimExit = if (isPreview) ExitTransition.None else fadeOut(tween(durationMillis = 160))
    val sheetEnter =
        if (isPreview) {
            EnterTransition.None
        } else {
            slideInVertically(
                animationSpec = spring(dampingRatio = DIAL_SHEET_DAMPING_RATIO, stiffness = DIAL_SHEET_STIFFNESS),
                initialOffsetY = { sheetStartOffsetPx },
            )
        }
    val sheetExit =
        if (isPreview) {
            ExitTransition.None
        } else {
            slideOutVertically(
                animationSpec = spring(dampingRatio = DIAL_SHEET_DAMPING_RATIO, stiffness = DIAL_SHEET_STIFFNESS),
                targetOffsetY = { sheetStartOffsetPx },
            )
        }
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.fillMaxSize(),
            enter = scrimEnter,
            exit = scrimExit,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x47252826))
                        .noRippleClickable(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(DIAL_HEIGHT + bottomInset),
            enter = sheetEnter,
            exit = sheetExit,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(groups) {
                            val velocityTracker = VelocityTracker()
                            var dragStartProgress = dialProgressState
                            var dragDistancePx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragStartProgress = dialProgressState
                                    isDragging = true
                                    onDialProgressChangeState(dragStartProgress)
                                    dragDistancePx = 0f
                                    velocityTracker.resetTracking()
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    dragDistancePx += dragAmount
                                    val progress =
                                        (dragStartProgress - dragDistancePx / dragStepPx)
                                            .coerceIn(0f, groups.lastIndex.toFloat())
                                    onDialProgressChangeState(progress)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val velocityBoost =
                                        (velocityTracker.calculateVelocity().x / densityScale / 1400f)
                                            .coerceIn(-0.6f, 0.6f)
                                    val progress =
                                        (dragStartProgress - dragDistancePx / dragStepPx)
                                            .coerceIn(0f, groups.lastIndex.toFloat())
                                    val nextIndex =
                                        (progress - velocityBoost)
                                            .roundToInt()
                                            .coerceIn(0, groups.lastIndex)
                                    val nextGroup = groups[nextIndex]
                                    onDialProgressSettleState(nextIndex.toFloat())
                                    if (nextIndex != selectedIndexState) {
                                        onSelectedGroupChangeState(nextGroup)
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    onDialProgressSettleState(selectedIndexState.toFloat())
                                },
                            )
                        },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val arcTop = 8.dp.toPx()
                    val radius = size.width * DIAL_BACKGROUND_RADIUS_TO_WIDTH
                    val center = Offset(size.width / 2f, arcTop + radius)
                    drawCircle(color = Color.White, radius = radius, center = center)
                    drawCircle(
                        color = Color(0xff252826),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.3.dp.toPx()),
                    )
                }

                Text(
                    text = stringResource(Res.string.record_group_select),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 23.dp),
                    color = Color(0xff252826),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )

                Canvas(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 53.dp)
                            .size(width = 26.dp, height = 22.dp),
                ) {
                    val pointer =
                        Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                    drawPath(pointer, Color(0xff252826))
                }

                val items =
                    groups
                        .mapIndexedNotNull { index, group ->
                            val slot = index - visibleDialProgress
                            group.takeIf { abs(slot) < 2.8f }?.let { Triple(it, index, slot) }
                        }.sortedByDescending { abs(it.third) }

                items.forEach { (group, index, slot) ->
                    key(group.id) {
                        val angle = slot * DIAL_STEP_DEGREES * PI.toFloat() / 180f
                        val centerX = constraints.maxWidth / 2f + sin(angle) * dialRadiusPx
                        val centerY = dialCenterYPx - cos(angle) * dialRadiusPx
                        val scale = max(0.82f, 1f - abs(slot) * 0.1f)

                        Box(
                            modifier =
                                Modifier
                                    .offset {
                                        IntOffset(
                                            x = (centerX - dialItemHalfSizePx).roundToInt(),
                                            y = (centerY - dialItemHalfSizePx).roundToInt(),
                                        )
                                    }.size(DIAL_ITEM_SIZE)
                                    .graphicsLayer {
                                        rotationZ = slot * 9f
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = 1f - abs(slot).coerceAtMost(1f) * 0.42f
                                    }.zIndex(3f - abs(slot))
                                    .noRippleClickable(enabled = abs(index - selectedIndex) <= 2) {
                                        if (index != selectedIndex) {
                                            onDialProgressSettleState(index.toFloat())
                                            onSelectedGroupChangeState(group)
                                        }
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (group.showStamp) {
                                GroupSelectionStamp(stamp = group.stamp, size = DIAL_ITEM_SIZE)
                            } else {
                                GroupSelectionStamp(stamp = null, size = DIAL_ITEM_SIZE, emptyLabel = group.name)
                            }
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 198.dp)
                            .height(20.dp)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selectedGroup.name,
                        color = Color(0xff7d837a),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp + bottomInset)
                            .size(width = 235.dp, height = 41.dp)
                            .raisedButtonBorder(CircleShape, interactionSource = interactionSource)
                            .clip(CircleShape)
                            .background(Color(0xffffe164))
                            .noRippleClickable(
                                interactionSource = interactionSource,
                                onClick = { onComplete(selectedGroup) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.record_group_select_done),
                        color = Color(0xff252826),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Preview(name = "그룹 선택 다이얼", widthDp = 402, heightDp = 874, showBackground = true)
@Preview(name = "그룹 선택 다이얼 큰 화면", widthDp = 440, heightDp = 956, showBackground = true)
@Composable
private fun GroupSelectorContentPreview() {
    GroupSelectorPreviewContent(
        defaultGroup = GroupSelectorGroupUiModel("none", "없음", null),
        initialSelectedGroupId = "baemin",
    )
}

@Preview(name = "감정 목록 그룹 선택 · 전체", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun AllGroupsSelectorContentPreview() {
    GroupSelectorPreviewContent(
        defaultGroup = GroupSelectorGroupUiModel("all", "전체", null, showStamp = false),
        initialSelectedGroupId = "all",
    )
}

@Composable
private fun GroupSelectorPreviewContent(
    defaultGroup: GroupSelectorGroupUiModel,
    initialSelectedGroupId: String,
) {
    val groups =
        listOf(
            defaultGroup,
            GroupSelectorGroupUiModel(
                "baemin",
                "배민",
                StampAppearanceUiModel("배민", StampShapeId.TICKET, 0xFFFFE164, 0xFF252826),
            ),
            GroupSelectorGroupUiModel(
                "megabox",
                "메가박스",
                StampAppearanceUiModel("메박", StampShapeId.FLOWER, 0xFFACD9EE, 0xFF252826),
            ),
        )
    var selectedGroupId by remember { mutableStateOf(initialSelectedGroupId) }
    val selectedIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)
    var dialProgress by remember { mutableStateOf(selectedIndex.toFloat()) }

    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xffbfc2c5)),
        ) {
            GroupSelectorContent(
                isVisible = true,
                groups = groups,
                selectedGroupId = selectedGroupId,
                dialProgress = dialProgress,
                onDialProgressChange = { dialProgress = it },
                onDialProgressSettle = { dialProgress = it },
                onSelectedGroupChange = { selectedGroupId = it.id },
                onDismiss = {},
                onComplete = { selectedGroupId = it.id },
            )
        }
    }
}
