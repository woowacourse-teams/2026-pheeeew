package com.pheeeew.feature.component.stamp

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
private fun GroupStampContent(
    appearance: StampAppearanceUiModel,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    AppearanceGroupStamp(appearance = appearance, size = size, modifier = modifier)
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
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

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
            val textLayout = appearance.label.toStampTextLayout()
            val textWidth = maxWidth * textArea.widthFraction
            val textHeight = maxHeight * textArea.heightFraction
            val textX = maxWidth * textArea.centerX - textWidth / 2
            val textY = maxHeight * textArea.centerY - textHeight / 2
            val fontSize = stampFontSize(textLayout, size, fontScale = density.fontScale)
            val lineHeight = stampLineHeight(fontSize)
            val textStyle =
                LocalTextStyle.current.copy(
                    color = textColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            val textWidthPx = with(density) { textWidth.roundToPx().coerceAtLeast(1) }
            val measuredLines =
                textMeasurer
                    .measure(
                        text = textLayout.text,
                        style = textStyle,
                        overflow = TextOverflow.Clip,
                        maxLines = textLayout.characterCount.coerceAtLeast(1),
                        constraints = Constraints(minWidth = textWidthPx, maxWidth = textWidthPx),
                    ).lineCount
            val displayText = textLayout.balanceTwoLines(measuredLines)

            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = textX, y = textY)
                        .size(width = textWidth, height = textHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayText,
                    modifier = Modifier.fillMaxWidth(),
                    style = textStyle,
                    maxLines = textLayout.characterCount.coerceAtLeast(1),
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

internal fun stampFontSize(
    layout: StampTextLayout,
    stampSize: Dp,
    fontScale: Float = 1f,
): TextUnit {
    val baseFontSize =
        when (layout.characterCount) {
            1 -> 28f
            2 -> 24f
            3 -> 20f
            else -> 16f
        }
    // Keep size stable by label length; scale only with the stamp slot and cancel system font scaling.
    return (baseFontSize * stampSize.value / STAMP_REFERENCE_SIZE_DP / fontScale.coerceAtLeast(0.01f)).sp
}

internal fun stampLineHeight(fontSize: TextUnit): TextUnit = (fontSize.value * STAMP_LINE_HEIGHT_FACTOR).sp

internal data class StampTextLayout(
    val text: String,
    val characterCount: Int,
)

/** Balance four characters only when the available width makes the label two lines. */
internal fun StampTextLayout.balanceTwoLines(lineCount: Int): String {
    if (characterCount != 4 || lineCount != 2 || '\n' in text) return text

    val splitIndex = text.nextCodePointIndex(text.nextCodePointIndex(0))
    return text.substring(0, splitIndex) + "\n" + text.substring(splitIndex)
}

/** Keeps the original label so Text can wrap it at the actual text-area width. */
internal fun String.toStampTextLayout(): StampTextLayout {
    var characterCount = 0
    var index = 0
    while (index < length) {
        val nextIndex = nextCodePointIndex(index)
        characterCount += 1
        index = nextIndex
    }

    return StampTextLayout(text = this, characterCount = characterCount)
}

private fun String.nextCodePointIndex(index: Int): Int {
    val isSurrogatePair = this[index] in HIGH_SURROGATES && index + 1 < length && this[index + 1] in LOW_SURROGATES
    return index + if (isSurrogatePair) 2 else 1
}

private const val STAMP_REFERENCE_SIZE_DP = 96f
private const val STAMP_LINE_HEIGHT_FACTOR = 1.2f
private val HIGH_SURROGATES = '\uD800'..'\uDBFF'
private val LOW_SURROGATES = '\uDC00'..'\uDFFF'

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

@Preview(name = "그룹 스탬프 문구 1자 · 모양 10종", widthDp = 360, heightDp = 160, showBackground = true)
@Composable
private fun GroupStampOneCharacterPreview() {
    GroupStampLengthPreview(label = "숨")
}

@Preview(name = "그룹 스탬프 문구 2자 · 모양 10종", widthDp = 360, heightDp = 160, showBackground = true)
@Composable
private fun GroupStampTwoCharacterPreview() {
    GroupStampLengthPreview(label = "기록")
}

@Preview(name = "그룹 스탬프 문구 3자 · 모양 10종", widthDp = 360, heightDp = 160, showBackground = true)
@Composable
private fun GroupStampThreeCharacterPreview() {
    GroupStampLengthPreview(label = "우리집")
}

@Preview(name = "그룹 스탬프 문구 4자 · 모양 10종", widthDp = 360, heightDp = 160, showBackground = true)
@Composable
private fun GroupStampFourCharacterPreview() {
    GroupStampLengthPreview(label = "우리모임")
}

@Composable
private fun GroupStampLengthPreview(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row {
                StampShapeId.entries.take(5).forEach { shape ->
                    GroupStamp(
                        appearance = sampleAppearance(shape).copy(label = label),
                        size = 56.dp,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
            Row {
                StampShapeId.entries.drop(5).forEach { shape ->
                    GroupStamp(
                        appearance = sampleAppearance(shape).copy(label = label),
                        size = 56.dp,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
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
