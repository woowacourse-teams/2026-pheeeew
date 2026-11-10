package com.pheeeew.feature.screens.collection.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import com.pheeeew.feature.screens.collection.card.CollectionCardUiModel
import pheeeew.shared.generated.resources.Res

@Composable
fun CalendarCard(
    card: CollectionCardUiModel,
    dayOfMonth: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(modifier.clip(shape).background(AppColors.Border)) {
        val imageScale = maxWidth.value / 300f
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 1.dp)
                .clip(shape)
                .background(AppColors.CollectionCardBackground)
                .border(1.dp, AppColors.Border, shape),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .border(0.5.dp, AppColors.CollectionCardDivider, RoundedCornerShape(2.5.dp)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
                    .padding(start = 2.dp, end = 2.dp, bottom = 5.dp),
            ) {
                AsyncImage(
                    model = card.characterImageUrl?.takeIf(String::isNotBlank),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(top = 50.dp * imageScale).fillMaxSize(),
                )
                AsyncImage(
                    model = card.textStampImageUrl?.takeIf(String::isNotBlank),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(78.dp * imageScale),
                )
                AsyncImage(
                    model = card.roundStampImageUrl?.takeIf(String::isNotBlank),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.BottomEnd).size(64.dp * imageScale),
                )
            }
        }
    }
}

@Preview(name = "달력 카드", widthDp = 56, heightDp = 68, showBackground = true)
@Composable
private fun CalendarCardPreview() {
    CollectionPreviewTheme {
        CalendarCard(
            card =
                CollectionCardUiModel(
                    id = "preview-card",
                    period = "2026.10.04",
                    title = "오늘도히유",
                    emotion = EmotionType.EXHAUSTED,
                    pressLabel = "프레스",
                    pressCount = 128,
                    stampLabel = "스탬프",
                    stampCount = 4,
                    characterImageUrl = Res.getUri("drawable/tmp_emotionimg.png"),
                    textStampImageUrl = Res.getUri("drawable/tmp_text_stamp.png"),
                    roundStampImageUrl = Res.getUri("drawable/tmp_round_stamp.png"),
                ),
            dayOfMonth = 4,
            isToday = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
