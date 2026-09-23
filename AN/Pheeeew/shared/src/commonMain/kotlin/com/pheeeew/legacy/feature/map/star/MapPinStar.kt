package com.pheeeew.legacy.feature.map.star

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 네이티브 지도 핀과 같은 비율의 광원, 16각 별, 중심 별을 그립니다. */
@Composable
fun MapPinStar(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val outerPath = remember { Path() }
    val corePath = remember { Path() }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val diameter = size.minDimension
        drawCircle(
            brush =
                Brush.radialGradient(
                    colorStops =
                        arrayOf(
                            0f to color.copy(alpha = 0.7f),
                            0.52f to color.copy(alpha = 0.24f),
                            1f to Color.Transparent,
                        ),
                    center = center,
                    radius = diameter / 2f,
                ),
            center = center,
            radius = diameter / 2f,
        )
        drawMapStar(
            path = outerPath,
            center = center,
            majorRadius = diameter * OUTER_MAJOR_RADIUS_RATIO,
            diagonalRadius = diameter * OUTER_DIAGONAL_RADIUS_RATIO,
            innerRadius = diameter * OUTER_INNER_RADIUS_RATIO,
            color = color,
        )
        drawMapStar(
            path = corePath,
            center = center,
            majorRadius = diameter * CORE_MAJOR_RADIUS_RATIO,
            diagonalRadius = diameter * CORE_DIAGONAL_RADIUS_RATIO,
            innerRadius = diameter * CORE_INNER_RADIUS_RATIO,
            color = AppColors.Cream100,
        )
    }
}

private fun DrawScope.drawMapStar(
    path: Path,
    center: Offset,
    majorRadius: Float,
    diagonalRadius: Float,
    innerRadius: Float,
    color: Color,
) {
    path.reset()
    repeat(STAR_VERTEX_COUNT) { index ->
        val radius =
            when {
                index % 2 != 0 -> innerRadius
                index % 4 == 0 -> majorRadius
                else -> diagonalRadius
            }
        val angle = -PI.toFloat() / 2f + index * PI.toFloat() / 8f
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}

private const val STAR_VERTEX_COUNT = 16
private const val OUTER_MAJOR_RADIUS_RATIO = 31f / 96f
private const val OUTER_DIAGONAL_RADIUS_RATIO = 24f / 96f
private const val OUTER_INNER_RADIUS_RATIO = 15f / 96f
private const val CORE_MAJOR_RADIUS_RATIO = 20f / 96f
private const val CORE_DIAGONAL_RADIUS_RATIO = 15f / 96f
private const val CORE_INNER_RADIUS_RATIO = 10f / 96f
