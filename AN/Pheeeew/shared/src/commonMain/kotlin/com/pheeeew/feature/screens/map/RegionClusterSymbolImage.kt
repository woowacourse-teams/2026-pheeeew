package com.pheeeew.feature.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.map_cluster_glint_left
import pheeeew.shared.generated.resources.map_cluster_glint_left_edge
import pheeeew.shared.generated.resources.map_cluster_glint_right
import pheeeew.shared.generated.resources.map_cluster_glint_right_edge
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas as CanvasComposable

/** Dimensions match the native map display at an icon scale of 1. */
private const val BUBBLE_HEIGHT_DP = 32f
private const val ICON_SIZE_DP = 26f
private const val BADGE_SIZE_DP = 15f
private const val LABEL_ICON_GAP_DP = 4f
private const val LABEL_PADDING_DP = 8f
private const val ICON_RIGHT_PADDING_DP = 8f

@Composable
internal fun rememberRegionClusterSymbolImages(regions: List<RegionClusterUiModel>): List<EmotionPinSymbolImage> {
    val appearances = remember(regions) { regions.distinctBy(RegionClusterUiModel::symbolImageKey) }
    val captured = remember { mutableStateMapOf<String, EmotionPinSymbolImage>() }
    val desiredKeys = remember(appearances) { appearances.mapTo(mutableSetOf(), RegionClusterUiModel::symbolImageKey) }
    LaunchedEffect(desiredKeys) { captured.keys.retainAll(desiredKeys) }
    appearances.forEach { region ->
        key(region.symbolImageKey()) {
            RasterizeRegionCluster(region) { captured[it.key] = it }
        }
    }
    return appearances.mapNotNull { captured[it.symbolImageKey()] }
}

@Composable
private fun RasterizeRegionCluster(
    region: RegionClusterUiModel,
    onRasterized: (EmotionPinSymbolImage) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val drawing = regionClusterDrawing(region)
    LaunchedEffect(region.symbolImageKey(), density, layoutDirection, drawing) {
        val image =
            withContext(pinSymbolRasterDispatcher()) {
                val bitmap = ImageBitmap(drawing.size.width, drawing.size.height)
                CanvasDrawScope().draw(
                    density,
                    layoutDirection,
                    Canvas(bitmap),
                    Size(drawing.size.width.toFloat(), drawing.size.height.toFloat()),
                    drawing.draw,
                )
                bitmap.toEmotionPinSymbolImage(region.symbolImageKey(), (density.density * 160).roundToInt())
            }
        if (image.hasVisiblePixels) onRasterized(image)
    }
}

private class RegionClusterDrawing(
    val size: IntSize,
    val draw: DrawScope.() -> Unit,
)

/** Shared drawing for native map raster images and synchronous Compose previews. */
@Composable
private fun regionClusterDrawing(region: RegionClusterUiModel): RegionClusterDrawing {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val leftGlint = painterResource(Res.drawable.map_cluster_glint_left)
    val leftEdge = painterResource(Res.drawable.map_cluster_glint_left_edge)
    val rightGlint = painterResource(Res.drawable.map_cluster_glint_right)
    val rightEdge = painterResource(Res.drawable.map_cluster_glint_right_edge)
    val emotionPainter = region.representativeEmotion?.let { painterResource(it.icon) }
    val clusterFont = notoSansKrFontFamily()
    val labelStyle =
        TextStyle(
            color = Color(0xFF293540),
            fontSize = 10.sp,
            fontFamily = clusterFont,
            fontWeight = FontWeight.SemiBold,
        )
    val badgeStyle =
        TextStyle(
            color = Color.White,
            fontSize = 7.sp,
            fontFamily = clusterFont,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

    return remember(
        region.symbolImageKey(),
        density,
        textMeasurer,
        labelStyle,
        badgeStyle,
        emotionPainter,
        leftGlint,
        leftEdge,
        rightGlint,
        rightEdge,
    ) {
        val label = textMeasurer.measure(region.name, style = labelStyle)
        val badgeText = region.count.toString()
        val badge = textMeasurer.measure(badgeText, style = badgeStyle)
        val scale = density.density
        val labelWidthDp = label.size.width / scale
        val bubbleWidthDp =
            LABEL_PADDING_DP + labelWidthDp +
                if (emotionPainter == null) {
                    LABEL_PADDING_DP
                } else {
                    LABEL_ICON_GAP_DP + ICON_SIZE_DP + ICON_RIGHT_PADDING_DP
                }
        val badgeDiameterDp = maxOf(BADGE_SIZE_DP, badge.size.width / scale + 6f)
        val bubbleTopDp = maxOf(8f, badgeDiameterDp / 2f + 1f)
        val rightMarginDp = maxOf(10f, badgeDiameterDp / 2f + 2f)
        val width = ((8f + bubbleWidthDp + rightMarginDp) * scale).roundToInt()
        val height = ((bubbleTopDp + BUBBLE_HEIGHT_DP + 4f) * scale).roundToInt()
        RegionClusterDrawing(IntSize(width, height)) {
            val bubbleTop = bubbleTopDp * scale
            val bubbleLeft = 8f * scale
            val bubbleWidth = bubbleWidthDp * scale
            val bubbleHeight = BUBBLE_HEIGHT_DP * scale
            val radius = bubbleHeight / 2f
            drawRoundRect(
                color = Color(0x2699A5D0),
                topLeft = Offset(bubbleLeft, bubbleTop + 2f * scale),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(radius),
            )
            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        listOf(Color(0xB8DFF3FF), Color(0x4DFFFFFF), Color(0xA6DFD3F5)),
                        startY = bubbleTop,
                        endY = bubbleTop + bubbleHeight,
                    ),
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(radius),
            )
            drawRoundRect(
                color = Color(0x99BC9CEB),
                topLeft = Offset(bubbleLeft, bubbleTop),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(radius),
                style = Stroke(width = 0.25f * scale),
            )
            translate(bubbleLeft + 2.5f * scale, bubbleTop + 0.55f * scale) {
                with(leftGlint) { draw(Size(11.7875f * scale, 9.25f * scale)) }
            }
            translate(bubbleLeft + 2.5f * scale, bubbleTop + 0.1f * scale) {
                with(leftEdge) { draw(Size(12.75f * scale, 7.5f * scale)) }
            }
            translate(bubbleLeft + bubbleWidth - 15f * scale, bubbleTop + 20f * scale) {
                with(rightGlint) { draw(Size(12.7f * scale, 8.6f * scale)) }
            }
            translate(bubbleLeft + bubbleWidth - 5f * scale, bubbleTop + 22.5f * scale) {
                with(rightEdge) { draw(Size(2.875f * scale, 4.625f * scale)) }
            }
            drawText(
                label,
                topLeft =
                    Offset(
                        bubbleLeft + LABEL_PADDING_DP * scale,
                        bubbleTop + (bubbleHeight - label.size.height) / 2f,
                    ),
            )
            emotionPainter?.let { painter ->
                val ratio =
                    painter.intrinsicSize.let { intrinsic ->
                        if (intrinsic.width.isFinite() &&
                            intrinsic.height > 0f
                        ) {
                            intrinsic.width / intrinsic.height
                        } else {
                            1f
                        }
                    }
                val iconHeight = ICON_SIZE_DP * scale
                val iconWidth = iconHeight * ratio
                val iconLeft =
                    bubbleLeft + (LABEL_PADDING_DP + labelWidthDp + LABEL_ICON_GAP_DP) * scale +
                        (ICON_SIZE_DP * scale - iconWidth) / 2f
                val iconTop = bubbleTop + (bubbleHeight - iconHeight) / 2f
                translate(iconLeft, iconTop) {
                    with(painter) { draw(Size(iconWidth, iconHeight)) }
                }
            }
            val badgeDiameter = badgeDiameterDp * scale
            val badgeCenter = Offset(bubbleLeft + bubbleWidth - 0.5f * scale, bubbleTop + 3f * scale)
            drawCircle(Color.White, radius = badgeDiameter / 2f, center = badgeCenter)
            drawCircle(Color(0xFFFF625F), radius = badgeDiameter / 2f - 0.75f * scale, center = badgeCenter)
            drawText(
                badge,
                topLeft =
                    Offset(
                        badgeCenter.x - badge.size.width / 2f,
                        badgeCenter.y - badge.size.height / 2f,
                    ),
            )
        }
    }
}

@Preview(name = "클러스터 핀 · 지도 기본 크기", widthDp = 360, showBackground = true)
@Composable
private fun RegionClusterMapSizePreview() {
    RegionClusterPreviewSamples(displayScale = 1f)
}

@Preview(name = "클러스터 핀 · 디자인 확대 2배", widthDp = 420, showBackground = true)
@Composable
private fun RegionClusterDesignPreview() {
    RegionClusterPreviewSamples(displayScale = 2f)
}

@Composable
private fun RegionClusterPreviewSamples(displayScale: Float) {
    val samples =
        listOf(
            "서울" to 8L,
            "강남구" to 42L,
            "역삼동" to 123L,
            "부산광역시" to 999L,
            "성남시 분당구" to 12345L,
        )
    Column(
        modifier = Modifier.background(Color(0xFFECEAE5)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmotionTypeUiModel.entries.forEachIndexed { index, emotion ->
            val (name, count) = samples[index]
            RegionClusterPreviewPin(name, count, emotion, displayScale)
        }
        RegionClusterPreviewPin("감정 없음", 10L, null, displayScale)
    }
}

@Composable
private fun RegionClusterPreviewPin(
    name: String,
    count: Long,
    emotion: EmotionTypeUiModel?,
    displayScale: Float,
) {
    val drawing =
        regionClusterDrawing(
            RegionClusterUiModel("preview", name, 126.978, 37.5665, count, emotion),
        )
    val density = LocalDensity.current
    CanvasComposable(
        Modifier.size(
            with(density) { drawing.size.width.toDp() } * displayScale,
            with(density) { drawing.size.height.toDp() } * displayScale,
        ),
    ) {
        scale(displayScale, pivot = Offset.Zero) { drawing.draw(this) }
    }
}
