package com.pheeeew.feature.screens.map.record.location

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.BasicTopBar
import com.pheeeew.core.designsystem.component.SheetDragHandle
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.screens.map.overlay.MapControlButton
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.group.GroupSelectionStamp
import com.pheeeew.feature.screens.map.record.noRippleClickable
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_my_location
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RecordLocationSelectionContent(
    origin: GeoCoordinate?,
    selectedCoordinate: GeoCoordinate?,
    viewport: RecordMapViewport?,
    selectedEmotion: EmotionTypeUiModel,
    selectedGroupStamp: StampAppearanceUiModel?,
    isSubmitting: Boolean,
    canConfirm: Boolean,
    onCoordinateSelected: (Double, Double) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    onMyLocationClick: () -> Unit = {},
    isRequestingLocation: Boolean = false,
    showDragGuide: Boolean = true,
) {
    val density = LocalDensity.current.density
    val latestOnSelected by rememberUpdatedState(onCoordinateSelected)
    val latestCanMove by rememberUpdatedState(!isSubmitting)
    val latestViewport by rememberUpdatedState(viewport)
    val latestCoordinate by rememberUpdatedState(selectedCoordinate ?: origin)
    Box(modifier = modifier.fillMaxSize()) {
        // Draw only: the map keeps receiving pan and zoom gestures through the dim.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dim =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    if (origin != null && viewport != null && viewport.radius > 0f) {
                        val center = Offset(viewport.centerX * density, viewport.centerY * density)
                        val radius = viewport.radius * density
                        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
                    }
                }
            drawPath(dim, Color.Black.copy(alpha = 0.45f))
        }
        if (origin != null && viewport != null && viewport.radius > 0f) {
            val center = Offset(viewport.centerX * density, viewport.centerY * density)
            val radius = viewport.radius * density
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFF398CFF).copy(alpha = 0.14f), radius, center)
                    drawCircle(
                        Color(0xFF398CFF),
                        radius,
                        center,
                        style =
                            Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
                            ),
                    )
                    drawCircle(Color(0xFF398CFF).copy(alpha = 0.18f), 17.dp.toPx(), center)
                    drawCircle(Color.White, 9.dp.toPx(), center)
                    drawCircle(Color(0xFF398CFF), 6.dp.toPx(), center)
                }
                val selected = selectedCoordinate ?: origin
                val meters = distance(origin, selected)
                val angle = bearing(origin, selected)
                val offsetX = (sin(angle) * meters / RECORD_RADIUS_METERS * radius).toFloat()
                val offsetY = (-cos(angle) * meters / RECORD_RADIUS_METERS * radius).toFloat()
                var isStampPressed by remember(origin) { mutableStateOf(false) }
                val stampScale by animateFloatAsState(
                    targetValue = if (isStampPressed && !isSubmitting) 1.2f else 1f,
                    animationSpec = tween(durationMillis = 150),
                    label = "recordStampPressScale",
                )
                if (showDragGuide) {
                    RecordStampDragGuide(
                        modifier =
                            Modifier.offset {
                                IntOffset(
                                    (center.x + offsetX - 58.dp.toPx()).roundToInt(),
                                    (center.y + offsetY - 76.dp.toPx()).roundToInt(),
                                )
                            },
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(62.dp)
                            .offset {
                                IntOffset(
                                    (center.x + offsetX - 31.dp.toPx()).roundToInt(),
                                    (center.y + offsetY - 31.dp.toPx()).roundToInt(),
                                )
                            }.pointerInput(origin, isSubmitting) {
                                // Observe the press without consuming the stamp's drag events.
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    try {
                                        isStampPressed = latestCanMove
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                        } while (event.changes.any { it.pressed })
                                    } finally {
                                        isStampPressed = false
                                    }
                                }
                            }.pointerInput(origin, density) {
                                var draggedCenter = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        val currentViewport = latestViewport ?: return@detectDragGestures
                                        val coordinate = latestCoordinate ?: origin
                                        val angle = bearing(origin, coordinate)
                                        val scale = currentViewport.radius * density / RECORD_RADIUS_METERS
                                        val meters = distance(origin, coordinate)
                                        draggedCenter =
                                            Offset(
                                                currentViewport.centerX * density +
                                                    (sin(angle) * meters * scale).toFloat(),
                                                currentViewport.centerY * density -
                                                    (cos(angle) * meters * scale).toFloat(),
                                            )
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (!latestCanMove) return@detectDragGestures
                                    val currentViewport = latestViewport ?: return@detectDragGestures
                                    val currentCenter =
                                        Offset(
                                            currentViewport.centerX * density,
                                            currentViewport.centerY * density,
                                        )
                                    val currentRadius = currentViewport.radius * density
                                    draggedCenter += dragAmount
                                    val delta = draggedCenter - currentCenter
                                    val meters =
                                        (delta.getDistance() / currentRadius * RECORD_RADIUS_METERS)
                                            .coerceAtMost(RECORD_RADIUS_METERS - 0.001)
                                    val coordinate =
                                        destination(origin, meters, atan2(delta.x.toDouble(), -delta.y.toDouble()))
                                    latestOnSelected(coordinate.latitude, coordinate.longitude)
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    scaleX = stampScale
                                    scaleY = stampScale
                                }.requiredSize(70.dp)
                                .background(Color(0xFFFFF3BF).copy(alpha = 0.3f), CircleShape)
                                .border(2.dp, Color(0xFFE5BE28), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedGroupStamp == null) {
                            Image(
                                painter = painterResource(selectedEmotion.icon),
                                contentDescription = selectedEmotion.label,
                                modifier = Modifier.size(62.dp),
                            )
                        } else {
                            GroupSelectionStamp(stamp = selectedGroupStamp, size = 62.dp)
                        }
                    }
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(AppColors.Surface)
                    .noRippleClickable {}
                    .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicTopBar(
                title = "스탬프를 눌러서 옮겨주세요",
                onBack = onBack,
                enabled = !isSubmitting,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                        .border(1.dp, AppColors.TextSecondary.copy(alpha = 0.35f), AppShapes.Pill)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "내 위치에서 500m 내에서 찍을 수 있어요.",
                    color = AppColors.TextPrimary,
                    fontSize = 12.sp,
                )
            }
        }
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = AppColors.Primary,
            )
        }
        // A fixed panel, with no dismiss or swipe behavior.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            MapControlButton(
                icon = Res.drawable.ic_my_location,
                contentDescription = "내 위치로 이동",
                onClick = onMyLocationClick,
                enabled = !isRequestingLocation && !isSubmitting,
                modifier = Modifier.padding(end = 20.dp, bottom = 12.dp),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface, AppShapes.BottomSheet)
                        .noRippleClickable {}
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SheetDragHandle()
                Text(
                    text = "이 위치에 감정을 남길까요?",
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                    color = AppColors.GroupInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(width = AppBorders.Standard, color = AppColors.Border, shape = AppShapes.Button)
                            .background(
                                if (canConfirm && !isSubmitting) AppColors.Primary else AppColors.Gray100,
                                AppShapes.Button,
                            ).noRippleClickable(enabled = canConfirm && !isSubmitting, onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("여기에 남기기", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Preview(name = "위치 선택 · 현재 위치", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun RecordLocationSelectionPreview() {
    RecordLocationPreview(GeoCoordinate(37.5665, 126.9780))
}

@Preview(name = "위치 선택 · 이동한 스탬프", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun RecordLocationMovedPreview() {
    RecordLocationPreview(destination(GeoCoordinate(37.5665, 126.9780), 300.0, -2.0))
}

@Composable
private fun RecordLocationPreview(selected: GeoCoordinate) {
    Box(Modifier.fillMaxSize().background(Color(0xFFECEAE5))) {
        RecordLocationSelectionContent(
            origin = GeoCoordinate(37.5665, 126.9780),
            selectedCoordinate = selected,
            viewport = RecordMapViewport(201f, 437f, 153f),
            selectedEmotion = EmotionTypeUiModel.FRUSTRATED,
            selectedGroupStamp = null,
            isSubmitting = false,
            canConfirm = true,
            onCoordinateSelected = { _, _ -> },
            onConfirm = {},
            onBack = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
