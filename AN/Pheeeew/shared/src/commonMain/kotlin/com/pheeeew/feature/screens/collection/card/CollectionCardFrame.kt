package com.pheeeew.feature.screens.collection.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_card_corner

@Composable
internal fun CollectionCardFrame(
    cornerIconTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable (scale: Float) -> Unit,
) {
    BoxWithConstraints(modifier.aspectRatio(3f / 4f)) {
        val scale = maxWidth.value / 300f
        val cardShape = RoundedCornerShape(18.dp * scale)
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 3.dp * scale)
                .background(AppColors.Border, cardShape),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 3.dp * scale)
                .clip(cardShape)
                .background(AppColors.CollectionCardBackground)
                .border(2.dp * scale, AppColors.Border, cardShape),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp * scale)
                    .border(1.dp * scale, AppColors.CollectionCardDivider, RoundedCornerShape(12.dp * scale)),
            )
            content(scale)
        }
        CollectionCardCorners(
            tint = cornerIconTint,
            scale = scale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CollectionCardCorners(
    tint: Color,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        listOf(
            Alignment.TopStart to 180f,
            Alignment.TopEnd to 270f,
            Alignment.BottomStart to 90f,
            Alignment.BottomEnd to 0f,
        ).forEach { (alignment, rotation) ->
            Image(
                painter = painterResource(Res.drawable.ic_card_corner),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(alignment)
                        .padding(8.dp * scale)
                        .size(18.dp * scale)
                        .rotate(rotation),
                colorFilter = ColorFilter.tint(tint),
            )
        }
    }
}

@Preview(widthDp = 300, heightDp = 400, showBackground = true)
@Composable
private fun CollectionCardFramePreview() {
    CollectionPreviewTheme {
        CollectionCardFrame(cornerIconTint = EmotionType.EXHAUSTED.representativeColor) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("카드 프레임", color = AppColors.TextPrimary, textAlign = TextAlign.Center)
            }
        }
    }
}
