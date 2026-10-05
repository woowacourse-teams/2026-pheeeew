package com.pheeeew.feature.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.map_cluster_glint_left
import pheeeew.shared.generated.resources.map_cluster_glint_left_edge
import pheeeew.shared.generated.resources.map_cluster_glint_right
import pheeeew.shared.generated.resources.map_cluster_glint_right_edge
import kotlin.math.roundToInt

/** The selected Figma bubble is drawn at one quarter of its 256 px design height. */
private const val BUBBLE_HEIGHT_DP = 64f
private const val ICON_SIZE_DP = 48f
private const val BADGE_SIZE_DP = 30f

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
    val textMeasurer = rememberTextMeasurer()
    val leftGlint = painterResource(Res.drawable.map_cluster_glint_left)
    val leftEdge = painterResource(Res.drawable.map_cluster_glint_left_edge)
    val rightGlint = painterResource(Res.drawable.map_cluster_glint_right)
    val rightEdge = painterResource(Res.drawable.map_cluster_glint_right_edge)
    val emotionPainter = region.representativeEmotion?.let { painterResource(it.icon) }
    val clusterFont = notoSansKrFontFamily()
    val labelStyle = TextStyle(color = Color(0xFF293540), fontSize = 20.sp, fontFamily = clusterFont, fontWeight = FontWeight.SemiBold)
    val badgeStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = clusterFont, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)

    LaunchedEffect(region.symbolImageKey(), density, layoutDirection, emotionPainter, leftGlint, leftEdge, rightGlint, rightEdge) {
        val label = textMeasurer.measure(region.name, style = labelStyle)
        val badgeText = region.count.toString()
        val badge = textMeasurer.measure(badgeText, style = badgeStyle)
        val scale = density.density
        val labelWidthDp = label.size.width / scale
        val bubbleWidthDp = 24f + labelWidthDp + (if (emotionPainter == null) 0f else 20f + ICON_SIZE_DP) + 24f
        val badgeDiameterDp = maxOf(BADGE_SIZE_DP, badge.size.width / scale + 12f)
        val bubbleTopDp = maxOf(16f, badgeDiameterDp / 2f + 2f)
        val rightMarginDp = maxOf(20f, badgeDiameterDp / 2f + 4f)
        val width = ((16f + bubbleWidthDp + rightMarginDp) * scale).roundToInt()
        val height = ((bubbleTopDp + BUBBLE_HEIGHT_DP + 8f) * scale).roundToInt()
        val image = withContext(pinSymbolRasterDispatcher()) {
            val bitmap = ImageBitmap(width, height)
            CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                val bubbleTop = bubbleTopDp * scale
                val bubbleLeft = 16f * scale
                val bubbleWidth = bubbleWidthDp * scale
                val bubbleHeight = BUBBLE_HEIGHT_DP * scale
                val radius = bubbleHeight / 2f
                drawRoundRect(
                    color = Color(0x2699A5D0),
                    topLeft = Offset(bubbleLeft, bubbleTop + 4f * scale),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xB8DFF3FF), Color(0x4DFFFFFF), Color(0xA6DFD3F5)),
                        startY = bubbleTop,
                        endY = bubbleTop + bubbleHeight,
                    ),
                    topLeft = Offset(bubbleLeft, bubbleTop),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                drawRoundRect(
                    color = Color(0x99BC9CEB),
                    topLeft = Offset(bubbleLeft, bubbleTop),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    style = Stroke(width = 0.5f * scale),
                )
                translate(bubbleLeft + 5f * scale, bubbleTop + 1.1f * scale) {
                    with(leftGlint) { draw(Size(23.575f * scale, 18.5f * scale)) }
                }
                translate(bubbleLeft + 5f * scale, bubbleTop + 0.2f * scale) {
                    with(leftEdge) { draw(Size(25.5f * scale, 15f * scale)) }
                }
                translate(bubbleLeft + bubbleWidth - 30f * scale, bubbleTop + 40f * scale) {
                    with(rightGlint) { draw(Size(25.4f * scale, 17.2f * scale)) }
                }
                translate(bubbleLeft + bubbleWidth - 10f * scale, bubbleTop + 45f * scale) {
                    with(rightEdge) { draw(Size(5.75f * scale, 9.25f * scale)) }
                }
                drawText(label, topLeft = Offset(bubbleLeft + 24f * scale, bubbleTop + (bubbleHeight - label.size.height) / 2f))
                emotionPainter?.let { painter ->
                    val ratio = painter.intrinsicSize.let { intrinsic ->
                        if (intrinsic.width.isFinite() && intrinsic.height > 0f) intrinsic.width / intrinsic.height else 1f
                    }
                    val iconHeight = ICON_SIZE_DP * scale
                    val iconWidth = iconHeight * ratio
                    val iconLeft = bubbleLeft + bubbleWidth - (44f + ICON_SIZE_DP / 2f) * scale + (ICON_SIZE_DP * scale - iconWidth) / 2f
                    val iconTop = bubbleTop + (bubbleHeight - iconHeight) / 2f
                    drawCircle(Color(0x26292B2A), radius = ICON_SIZE_DP / 2f * scale, center = Offset(iconLeft + iconWidth / 2f, iconTop + iconHeight / 2f + 2f * scale))
                    translate(iconLeft, iconTop) {
                        with(painter) { draw(Size(iconWidth, iconHeight)) }
                    }
                }
                val badgeDiameter = badgeDiameterDp * scale
                val badgeCenter = Offset(bubbleLeft + bubbleWidth - 1f * scale, bubbleTop + 6f * scale)
                drawCircle(Color.White, radius = badgeDiameter / 2f, center = badgeCenter)
                drawCircle(Color(0xFFFF625F), radius = badgeDiameter / 2f - 1.5f * scale, center = badgeCenter)
                drawText(badge, topLeft = Offset(badgeCenter.x - badge.size.width / 2f, badgeCenter.y - badge.size.height / 2f))
            }
            bitmap.toEmotionPinSymbolImage(region.symbolImageKey(), (scale * 160).roundToInt())
        }
        if (image.hasVisiblePixels) onRasterized(image)
    }
}
