package com.pheeeew.feature.screens.map.record.location

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import com.pheeeew.feature.screens.map.record.group.GroupSelectionStamp
import com.pheeeew.feature.screens.map.record.noRippleClickable
import org.jetbrains.compose.resources.painterResource
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
) {
    val density = LocalDensity.current.density
    val latestOnSelected by rememberUpdatedState(onCoordinateSelected)
    val latestCanMove by rememberUpdatedState(!isSubmitting)
    val latestViewport by rememberUpdatedState(viewport)
    val latestCoordinate by rememberUpdatedState(selectedCoordinate ?: origin)
    Box(modifier = modifier.fillMaxSize()) {
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
                Box(
                    modifier =
                        Modifier
                            .size(62.dp)
                            .offset {
                                IntOffset(
                                    (center.x + offsetX - 31.dp.toPx()).roundToInt(),
                                    (center.y + offsetY - 31.dp.toPx()).roundToInt(),
                                )
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
        Text(
            text = "원하는 위치에 남겨보세요!",
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 30.dp)
                    .background(Color.Black.copy(alpha = 0.6f), AppShapes.Pill)
                    .padding(horizontal = 32.dp, vertical = 8.dp),
            color = AppColors.Surface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = AppColors.Primary,
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(140.dp)
                        .height(48.dp)
                        .border(width = AppBorders.Standard, color = AppColors.Border, shape = AppShapes.Button)
                        .background(
                            AppColors.Primary,
                            AppShapes.Button,
                        ).noRippleClickable(enabled = canConfirm && !isSubmitting, onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Text("완료", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "돌아가기",
                modifier =
                    Modifier
                        .noRippleClickable(onClick = onBack)
                        .padding(8.dp),
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
            )
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
