package com.pheeeew.feature.screens.collection.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.size.SizeResolver
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.bagelFatOneFontFamily
import com.pheeeew.core.designsystem.theme.gaeguFontFamily
import com.pheeeew.feature.emotion.model.EmotionType
import com.pheeeew.feature.screens.collection.CollectionPreviewTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.app_name_kr
import pheeeew.shared.generated.resources.collection_image_load_failed
import pheeeew.shared.generated.resources.ic_bubble_icon
import pheeeew.shared.generated.resources.ic_highlighter_underline
import kotlin.math.roundToInt

enum class CollectionCardImageLoadState { LOADING, READY, FAILED }

@Composable
fun CollectionCard(
    uiModel: CollectionCardUiModel,
    modifier: Modifier = Modifier,
    onImageLoadState: (CollectionCardImageLoadState) -> Unit = {},
) {
    CollectionCardFrame(cornerIconTint = uiModel.emotion.representativeColor, modifier = modifier) { scale ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp * scale, vertical = 20.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CollectionCardHeader(
                period = uiModel.period,
                title = uiModel.title,
                scale = scale,
            )
            Spacer(Modifier.height(2.dp * scale))
            CollectionCardImageArea(
                characterImageUrl = uiModel.characterImageUrl,
                textStampImageUrl = uiModel.textStampImageUrl,
                roundStampImageUrl = uiModel.roundStampImageUrl,
                characterDescription = uiModel.title,
                onImageLoadState = onImageLoadState,
                scale = scale,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp * scale))
            CollectionCardStats(
                pressLabel = uiModel.pressLabel,
                pressCount = uiModel.pressCount,
                stampLabel = uiModel.stampLabel,
                stampCount = uiModel.stampCount,
                scale = scale,
            )
        }
    }
}

@Composable
private fun CollectionCardHeader(
    period: String,
    title: String,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_bubble_icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp * scale).clip(RoundedCornerShape(5.dp * scale)),
            )
            Text(
                text = stringResource(Res.string.app_name_kr),
                fontWeight = FontWeight.SemiBold,
                fontSize = (14 * scale).sp,
                lineHeight = (18 * scale).sp,
                color = AppColors.TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = period,
                fontFamily = gaeguFontFamily(),
                fontSize = (10 * scale).sp,
                lineHeight = (13 * scale).sp,
                color = AppColors.TextPrimary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp * scale))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp * scale)
                .heightIn(min = 30.dp * scale),
            contentAlignment = Alignment.Center,
        ) {
            val titleTextWidthPx = remember(title) { mutableIntStateOf(0) }
            val titleAutoSize =
                remember(scale) {
                    TextAutoSize.StepBased(
                        minFontSize = (12 * scale).sp,
                        maxFontSize = (24 * scale).sp,
                        stepSize = (0.25f * scale).sp,
                    )
                }
            val highlighterWidth = with(LocalDensity.current) { (titleTextWidthPx.intValue * 1.2f).toDp() }
            Image(
                painter = painterResource(Res.drawable.ic_highlighter_underline),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width(highlighterWidth)
                        .height(22.dp * scale),
            )
            Text(
                text = title,
                fontFamily = gaeguFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = (24 * scale).sp,
                autoSize = titleAutoSize,
                lineHeight = (28 * scale).sp,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                onTextLayout = { layoutResult ->
                    val textWidthPx =
                        if (layoutResult.lineCount > 0) {
                            (layoutResult.getLineRight(0) - layoutResult.getLineLeft(0)).roundToInt()
                        } else {
                            0
                        }
                    if (titleTextWidthPx.intValue != textWidthPx) {
                        titleTextWidthPx.intValue = textWidthPx
                    }
                },
            )
        }
    }
}

@Composable
private fun CollectionCardImageArea(
    characterImageUrl: String?,
    textStampImageUrl: String?,
    roundStampImageUrl: String?,
    characterDescription: String?,
    onImageLoadState: (CollectionCardImageLoadState) -> Unit = {},
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val characterModel = characterImageUrl?.takeIf(String::isNotBlank)
    val textStampModel = textStampImageUrl?.takeIf(String::isNotBlank)
    val roundStampModel = roundStampImageUrl?.takeIf(String::isNotBlank)
    val characterSizeResolver = rememberConstraintsSizeResolver()
    val textStampSizeResolver = rememberConstraintsSizeResolver()
    val roundStampSizeResolver = rememberConstraintsSizeResolver()
    val characterPainter = rememberCardImagePainter(characterModel, characterSizeResolver)
    val textStampPainter = rememberCardImagePainter(textStampModel, textStampSizeResolver)
    val roundStampPainter = rememberCardImagePainter(roundStampModel, roundStampSizeResolver)
    val characterState by characterPainter.state.collectAsState()
    val textStampState by textStampPainter.state.collectAsState()
    val roundStampState by roundStampPainter.state.collectAsState()
    val hasError =
        (characterModel != null && characterState is AsyncImagePainter.State.Error) ||
            (textStampModel != null && textStampState is AsyncImagePainter.State.Error) ||
            (roundStampModel != null && roundStampState is AsyncImagePainter.State.Error)
    val imagesReady =
        !hasError &&
            (characterModel == null || characterState is AsyncImagePainter.State.Success) &&
            (textStampModel == null || textStampState is AsyncImagePainter.State.Success) &&
            (roundStampModel == null || roundStampState is AsyncImagePainter.State.Success)

    LaunchedEffect(imagesReady, hasError) {
        onImageLoadState(
            when {
                hasError -> CollectionCardImageLoadState.FAILED
                imagesReady -> CollectionCardImageLoadState.READY
                else -> CollectionCardImageLoadState.LOADING
            },
        )
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        if (hasError) {
            Text(
                text = stringResource(Res.string.collection_image_load_failed),
                color = AppColors.TextPrimary,
                fontSize = (12 * scale).sp,
                lineHeight = (18 * scale).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp * scale),
            )
        } else {
            Image(
                painter = characterPainter,
                contentDescription = characterDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(top = 50.dp * scale).fillMaxSize().then(characterSizeResolver),
            )
            Image(
                painter = textStampPainter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .align(
                            Alignment.TopCenter,
                        ).fillMaxWidth()
                        .height(78.dp * scale)
                        .then(textStampSizeResolver),
            )
            Image(
                painter = roundStampPainter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(64.dp * scale)
                        .then(roundStampSizeResolver),
            )
        }
    }
}

@Composable
private fun rememberCardImagePainter(
    imageUrl: String?,
    sizeResolver: SizeResolver,
): AsyncImagePainter {
    val context = LocalPlatformContext.current
    val request =
        remember(context, imageUrl, sizeResolver) {
            ImageRequest
                .Builder(context)
                .data(imageUrl)
                .size(sizeResolver)
                .build()
        }
    return rememberAsyncImagePainter(model = request, contentScale = ContentScale.Fit)
}

@Composable
private fun CollectionCardStats(
    pressLabel: String,
    pressCount: Long,
    stampLabel: String,
    stampCount: Long,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp * scale)
            .border(1.dp * scale, AppColors.CollectionCardDivider, RoundedCornerShape(14.dp * scale))
            .padding(vertical = 8.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionCardStat(
            label = pressLabel,
            count = pressCount,
            scale = scale,
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(color = AppColors.CollectionCardDivider, modifier = Modifier.height(42.dp * scale))
        CollectionCardStat(
            label = stampLabel,
            count = stampCount,
            scale = scale,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CollectionCardStat(
    label: String,
    count: Long,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 8.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            fontFamily = gaeguFontFamily(),
            fontSize = (11 * scale).sp,
            lineHeight = (15 * scale).sp,
            color = AppColors.TextPrimary,
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().height(32.dp * scale), contentAlignment = Alignment.Center) {
            val countText = "${count}회"
            val countTextWidthPx = remember(countText) { mutableIntStateOf(0) }
            val countAutoSize =
                remember(scale) {
                    TextAutoSize.StepBased(
                        minFontSize = (8 * scale).sp,
                        maxFontSize = (18 * scale).sp,
                        stepSize = (0.25f * scale).sp,
                    )
                }
            val highlighterWidth = with(LocalDensity.current) { (countTextWidthPx.intValue * 1.2f).toDp() }
            Image(
                painter = painterResource(Res.drawable.ic_highlighter_underline),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.align(Alignment.BottomCenter).width(highlighterWidth).height(20.dp * scale),
            )
            Text(
                text = countText,
                fontFamily = bagelFatOneFontFamily(),
                fontWeight = FontWeight.Normal,
                fontSize = (18 * scale).sp,
                autoSize = countAutoSize,
                lineHeight = (24 * scale).sp,
                color = AppColors.TextPrimary,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                onTextLayout = { layoutResult ->
                    val textWidthPx =
                        if (layoutResult.lineCount > 0) {
                            (layoutResult.getLineRight(0) - layoutResult.getLineLeft(0)).roundToInt()
                        } else {
                            0
                        }
                    if (countTextWidthPx.intValue != textWidthPx) {
                        countTextWidthPx.intValue = textWidthPx
                    }
                },
            )
        }
    }
}

@Preview(widthDp = 300)
@Composable
private fun CollectionCardHeaderComponentPreview() {
    CollectionPreviewTheme {
        CollectionCardHeader(period = "2026.10.18", title = "만성 퇴근 희망자", scale = 1f)
    }
}

@Preview(widthDp = 300, heightDp = 240)
@Composable
private fun CollectionCardImageAreaComponentPreview() {
    CollectionPreviewTheme {
        CollectionCardImageArea(
            characterImageUrl = Res.getUri("drawable/tmp_emotionimg.png"),
            textStampImageUrl = Res.getUri("drawable/tmp_text_stamp.png"),
            roundStampImageUrl = Res.getUri("drawable/tmp_round_stamp.png"),
            characterDescription = null,
            scale = 1f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(widthDp = 300, showBackground = true)
@Composable
private fun CollectionCardStatsComponentPreview() {
    CollectionPreviewTheme {
        CollectionCardStats(pressLabel = "지침 프레스", pressCount = 312, stampLabel = "지침 스탬프", stampCount = 23, scale = 1f)
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionCardPreview() {
    CollectionPreviewTheme {
        CollectionCard(
            uiModel = collectionCardPreviewUiModel(EmotionType.EXHAUSTED, withImages = true),
            modifier = Modifier.size(300.dp, 400.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyCollectionCardPreview() {
    CollectionPreviewTheme {
        CollectionCard(
            uiModel = collectionCardPreviewUiModel(EmotionType.EXHAUSTED),
            modifier = Modifier.size(300.dp, 400.dp),
        )
    }
}

private fun collectionCardPreviewUiModel(
    emotion: EmotionType,
    withImages: Boolean = false,
) = CollectionCardUiModel(
    id = "preview-${emotion.name}",
    period = "2026.10.18",
    title = "만성 퇴근 희망자",
    emotion = emotion,
    pressLabel = "지침 프레스",
    pressCount = 312_000,
    stampLabel = "지침 스탬프",
    stampCount = 23,
    characterImageUrl = Res.getUri("drawable/tmp_emotionimg.png").takeIf { withImages },
    textStampImageUrl = Res.getUri("drawable/tmp_text_stamp.png").takeIf { withImages },
    roundStampImageUrl = Res.getUri("drawable/tmp_round_stamp.png").takeIf { withImages },
)
