package com.pheeeew.feature.screens.group.create.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_color_hue
import pheeeew.shared.generated.resources.group_create_color_plane
import pheeeew.shared.generated.resources.group_create_color_saturation_down
import pheeeew.shared.generated.resources.group_create_color_saturation_up
import pheeeew.shared.generated.resources.group_create_color_value_down
import pheeeew.shared.generated.resources.group_create_color_value_up

@Composable
internal fun StampColorPicker(
    selection: StampColorSelection,
    onSelectionChanged: (StampColorSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onSelectionChanged)
    val hueDescription = stringResource(Res.string.group_create_color_hue)
    val planeDescription = stringResource(Res.string.group_create_color_plane)
    val saturationUp = stringResource(Res.string.group_create_color_saturation_up)
    val saturationDown = stringResource(Res.string.group_create_color_saturation_down)
    val valueUp = stringResource(Res.string.group_create_color_value_up)
    val valueDown = stringResource(Res.string.group_create_color_value_down)

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        SaturationValuePlane(
            selection = selection,
            contentDescription = planeDescription,
            actionLabels = listOf(saturationUp, saturationDown, valueUp, valueDown),
            onSelectionChanged = onSelectionChanged,
            modifier = Modifier.fillMaxWidth().height(82.dp),
        )
        HueSlider(
            hueDegrees = selection.hueDegrees,
            contentDescription = hueDescription,
            onHueChanged = { hue -> onChange(selection.copy(hueDegrees = hue)) },
            modifier = Modifier.fillMaxWidth().height(36.dp),
        )
    }
}

@Composable
private fun SaturationValuePlane(
    selection: StampColorSelection,
    contentDescription: String,
    actionLabels: List<String>,
    onSelectionChanged: (StampColorSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onSelectionChanged)
    val currentSelection by rememberUpdatedState(selection)
    val hue = selection.hueDegrees
    val markerSaturation = selection.saturation
    val markerValue = selection.value
    val hueColor = remember(hue) { Color.hsv(hue, 1f, 1f) }
    val whiteGradient = remember { Brush.horizontalGradient(listOf(Color.White, Color.Transparent)) }
    val blackGradient = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black)) }
    Canvas(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        if (size.width > 0 && size.height > 0) {
                            onChange(
                                currentSelection.copy(
                                    saturation = (position.x / size.width).coerceIn(0f, 1f),
                                    value = (1f - position.y / size.height).coerceIn(0f, 1f),
                                ),
                            )
                        }
                    }
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            if (size.width > 0 && size.height > 0) {
                                onChange(
                                    currentSelection.copy(
                                        saturation = (position.x / size.width).coerceIn(0f, 1f),
                                        value = (1f - position.y / size.height).coerceIn(0f, 1f),
                                    ),
                                )
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (size.width > 0 && size.height > 0) {
                                onChange(
                                    currentSelection.copy(
                                        saturation = (change.position.x / size.width).coerceIn(0f, 1f),
                                        value = (1f - change.position.y / size.height).coerceIn(0f, 1f),
                                    ),
                                )
                            }
                        },
                    )
                }.semantics {
                    this.contentDescription = contentDescription
                    stateDescription =
                        "채도 ${(selection.saturation * 100).toInt()}%, 밝기 ${(selection.value * 100).toInt()}%"
                    customActions =
                        listOf(
                            CustomAccessibilityAction(actionLabels[0]) {
                                onSelectionChanged(
                                    selection.copy(saturation = (selection.saturation + 0.05f).coerceAtMost(1f)),
                                )
                                true
                            },
                            CustomAccessibilityAction(actionLabels[1]) {
                                onSelectionChanged(
                                    selection.copy(saturation = (selection.saturation - 0.05f).coerceAtLeast(0f)),
                                )
                                true
                            },
                            CustomAccessibilityAction(actionLabels[2]) {
                                onSelectionChanged(selection.copy(value = (selection.value + 0.05f).coerceAtMost(1f)))
                                true
                            },
                            CustomAccessibilityAction(actionLabels[3]) {
                                onSelectionChanged(selection.copy(value = (selection.value - 0.05f).coerceAtLeast(0f)))
                                true
                            },
                        )
                },
    ) {
        drawRect(color = hueColor)
        drawRect(brush = whiteGradient)
        drawRect(brush = blackGradient)

        val maximumRadius = minOf(size.width / 2f, size.height / 2f)
        val radius = 10.dp.toPx().coerceAtMost(maximumRadius)
        val center =
            Offset(
                x = (markerSaturation * size.width).coerceIn(radius, size.width - radius),
                y = ((1f - markerValue) * size.height).coerceIn(radius, size.height - radius),
            )
        drawCircle(color = Color.White, radius = radius, center = center)
        if (radius >= 2.dp.toPx()) {
            drawCircle(color = Color(0xFF202323), radius = radius, center = center, style = Stroke(2.dp.toPx()))
            drawCircle(color = Color.White, radius = radius - 2.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun HueSlider(
    hueDegrees: Float,
    contentDescription: String,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChange by rememberUpdatedState(onHueChanged)
    val hueBrush = remember { Brush.horizontalGradient(HUE_GRADIENT_COLORS) }
    Canvas(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        if (size.width > 0) onChange((position.x / size.width * 359.999f).coerceIn(0f, 359.999f))
                    }
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            if (size.width > 0) onChange((position.x / size.width * 359.999f).coerceIn(0f, 359.999f))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (size.width >
                                0
                            ) {
                                onChange((change.position.x / size.width * 359.999f).coerceIn(0f, 359.999f))
                            }
                        },
                    )
                }.semantics {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(hueDegrees, 0f..359.999f)
                    setProgress { progress ->
                        onHueChanged(progress.coerceIn(0f, 359.999f))
                        true
                    }
                },
    ) {
        val barHeight = 12.dp.toPx()
        val left = 8.dp.toPx()
        val right = size.width - left
        val top = (size.height - barHeight) / 2f
        drawRoundRect(
            brush = hueBrush,
            topLeft = Offset(left, top),
            size =
                androidx.compose.ui.geometry
                    .Size(right - left, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
        val center = Offset(left + (right - left) * hueDegrees / 360f, size.height / 2f)
        drawCircle(Color.White, radius = 11.5.dp.toPx(), center = center)
        drawCircle(Color(0xFF202323), radius = 11.5.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        drawLine(
            color = Color(0xFF202323),
            start = Offset(center.x, center.y - 5.dp.toPx()),
            end = Offset(center.x, center.y + 5.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private val HUE_GRADIENT_COLORS =
    listOf(
        Color.hsv(0f, 1f, 1f),
        Color.hsv(60f, 1f, 1f),
        Color.hsv(120f, 1f, 1f),
        Color.hsv(180f, 1f, 1f),
        Color.hsv(240f, 1f, 1f),
        Color.hsv(300f, 1f, 1f),
        Color.hsv(359.99f, 1f, 1f),
    )
