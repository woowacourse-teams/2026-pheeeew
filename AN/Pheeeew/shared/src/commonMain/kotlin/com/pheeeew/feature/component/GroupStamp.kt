package com.pheeeew.feature.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeCatalog
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.component.stamp.StampTextArea
import org.jetbrains.compose.resources.painterResource

/** 정사각형 슬롯 안에 모양의 비율을 유지해 그룹 스탬프를 그립니다. */
@Composable
fun GroupStamp(
    appearance: StampAppearanceUiModel,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    GroupStampContent(appearance = appearance, size = size, modifier = modifier)
}

/** 랭킹 화면의 기존 호출을 유지하기 위한 호환 진입점입니다. */
@Composable
fun GroupStamp(
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    GroupStampContent(
        appearance =
            StampAppearanceUiModel(
                label = label,
                shape = StampShapeId.CIRCLE,
                fillArgb = LEGACY_FILL_ARGB,
                textArgb = LEGACY_TEXT_ARGB,
            ),
        size = size,
        modifier = modifier,
        preserveLegacyCircleStyle = true,
    )
}

@Composable
private fun GroupStampContent(
    appearance: StampAppearanceUiModel,
    size: Dp,
    modifier: Modifier = Modifier,
    preserveLegacyCircleStyle: Boolean = false,
) {
    if (preserveLegacyCircleStyle) {
        LegacyCircleStamp(appearance = appearance, size = size, modifier = modifier)
    } else {
        AppearanceGroupStamp(appearance = appearance, size = size, modifier = modifier)
    }
}

@Composable
private fun AppearanceGroupStamp(
    appearance: StampAppearanceUiModel,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = StampShapeCatalog[appearance.shape]
    val fillColor = Color(appearance.fillArgb.toInt())
    val textColor = Color(appearance.textArgb.toInt())

    BoxWithConstraints(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        val stampWidth = if (shape.aspectRatio >= 1f) maxWidth else maxHeight * shape.aspectRatio
        val stampHeight = if (shape.aspectRatio >= 1f) maxWidth / shape.aspectRatio else maxHeight

        BoxWithConstraints(
            modifier = Modifier.size(width = stampWidth, height = stampHeight),
        ) {
            Image(
                painter = painterResource(shape.backdrop),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Image(
                painter = painterResource(shape.fill),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(fillColor),
            )
            shape.overlay?.let { overlay ->
                Image(
                    painter = painterResource(overlay),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }

            val textArea = shape.textArea
            val textWidth = maxWidth * textArea.widthFraction
            val textHeight = maxHeight * textArea.heightFraction
            val textX = maxWidth * textArea.centerX - textWidth / 2
            val textY = maxHeight * textArea.centerY - textHeight / 2

            Text(
                text = appearance.label,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = textX, y = textY)
                        .size(width = textWidth, height = textHeight),
                color = textColor,
                fontSize = stampFontSize(appearance.label, textArea, maxWidth, maxHeight),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 공통 표시 모델을 사용하면서 기존 랭킹 스탬프의 모양과 크기를 유지합니다. */
@Composable
private fun LegacyCircleStamp(
    appearance: StampAppearanceUiModel,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .border(2.dp, AppColors.RankingContent, CircleShape)
                .padding(4.dp)
                .background(AppColors.RankingAccent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = appearance.label,
            color = Color(appearance.textArgb.toInt()),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun stampFontSize(
    label: String,
    textArea: StampTextArea,
    stampWidth: Dp,
    stampHeight: Dp,
) = minOf(
    stampHeight.value * textArea.heightFraction / 1.35f,
    stampWidth.value * textArea.widthFraction / (label.length.coerceAtLeast(1) * 0.95f),
).coerceIn(minimumValue = 7f, maximumValue = 28f).sp

private const val LEGACY_FILL_ARGB = 0xFF9DE8D0L
private const val LEGACY_TEXT_ARGB = 0xFF15181BL

@Preview(name = "레거시 랭킹 원형")
@Composable
private fun GroupStampPreview() {
    GroupStamp("히유", 62.dp, Modifier.padding(16.dp))
}

@Preview(name = "그룹 스탬프 모양 10종")
@Composable
private fun GroupStampAppearancePreview() {
    Column(modifier = Modifier.padding(12.dp)) {
        Row {
            StampShapeId.entries.take(5).forEach { shape ->
                GroupStamp(
                    appearance = sampleAppearance(shape),
                    size = 54.dp,
                    modifier = Modifier.padding(3.dp),
                )
            }
        }
        Row {
            StampShapeId.entries.drop(5).forEach { shape ->
                GroupStamp(
                    appearance = sampleAppearance(shape),
                    size = 54.dp,
                    modifier = Modifier.padding(3.dp),
                )
            }
        }
        Row {
            GroupStamp(sampleAppearance(StampShapeId.CIRCLE), 32.dp, Modifier.padding(3.dp))
            GroupStamp(sampleAppearance(StampShapeId.CIRCLE), 48.dp, Modifier.padding(3.dp))
            GroupStamp(sampleAppearance(StampShapeId.CIRCLE), 62.dp, Modifier.padding(3.dp))
            GroupStamp(sampleAppearance(StampShapeId.CIRCLE), 120.dp, Modifier.padding(3.dp))
        }
    }
}

private fun sampleAppearance(shape: StampShapeId) =
    StampAppearanceUiModel(
        label = "",
        shape = shape,
        fillArgb = 0xFFF5B9D0L,
        textArgb = 0xFF22211EL,
    )
