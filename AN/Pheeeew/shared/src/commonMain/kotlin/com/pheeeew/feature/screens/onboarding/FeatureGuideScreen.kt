package com.pheeeew.feature.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.navigation.FlowBackHandler
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.guide_collection_card_accent
import pheeeew.shared.generated.resources.guide_collection_card_body
import pheeeew.shared.generated.resources.guide_collection_card_title
import pheeeew.shared.generated.resources.guide_collection_mode_accent
import pheeeew.shared.generated.resources.guide_collection_mode_body
import pheeeew.shared.generated.resources.guide_collection_mode_title
import pheeeew.shared.generated.resources.guide_collection_stats_accent
import pheeeew.shared.generated.resources.guide_collection_stats_title
import pheeeew.shared.generated.resources.guide_map_filter_accent
import pheeeew.shared.generated.resources.guide_map_filter_body
import pheeeew.shared.generated.resources.guide_map_filter_title
import pheeeew.shared.generated.resources.guide_map_list_accent
import pheeeew.shared.generated.resources.guide_map_list_body
import pheeeew.shared.generated.resources.guide_map_list_title
import pheeeew.shared.generated.resources.guide_map_record_accent
import pheeeew.shared.generated.resources.guide_map_record_body
import pheeeew.shared.generated.resources.guide_map_record_title
import pheeeew.shared.generated.resources.guide_press_emotion_accent
import pheeeew.shared.generated.resources.guide_press_emotion_body
import pheeeew.shared.generated.resources.guide_press_emotion_title
import pheeeew.shared.generated.resources.guide_press_mine_accent
import pheeeew.shared.generated.resources.guide_press_mine_body
import pheeeew.shared.generated.resources.guide_press_mine_title
import pheeeew.shared.generated.resources.guide_press_ranking_accent
import pheeeew.shared.generated.resources.guide_press_ranking_body
import pheeeew.shared.generated.resources.guide_press_ranking_title
import pheeeew.shared.generated.resources.onboarding_collection
import pheeeew.shared.generated.resources.onboarding_guide_collection_description
import pheeeew.shared.generated.resources.onboarding_guide_map
import pheeeew.shared.generated.resources.onboarding_guide_map_description
import pheeeew.shared.generated.resources.onboarding_guide_press
import pheeeew.shared.generated.resources.onboarding_guide_press_description
import pheeeew.shared.generated.resources.onboarding_guide_swipe_hint
import pheeeew.shared.generated.resources.onboarding_skip
import pheeeew.shared.generated.resources.onboarding_start
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private data class GuideHighlight(
    val bounds: Rect,
    val radius: Float,
    val cutout: Boolean = true,
)

private data class GuideArrow(
    val start: Offset,
    val control: Offset,
    val end: Offset,
)

private data class GuideAnnotation(
    val title: StringResource,
    val accent: StringResource,
    val body: StringResource?,
    val position: Offset,
    val width: Float,
    val centered: Boolean = false,
)

private data class FeatureGuidePage(
    val screen: DrawableResource,
    val description: StringResource,
    val highlights: List<GuideHighlight>,
    val arrows: List<GuideArrow>,
    val annotations: List<GuideAnnotation>,
    val buttonTop: Float,
    val collection: Boolean = false,
)

private val featureGuidePages =
    listOf(
        FeatureGuidePage(
            screen = Res.drawable.onboarding_guide_map,
            description = Res.string.onboarding_guide_map_description,
            highlights =
                listOf(
                    GuideHighlight(Rect(337f, 50f, 743f, 176f), 63f),
                    GuideHighlight(Rect(906f, 199f, 1050f, 343f), 72f),
                    GuideHighlight(Rect(385f, 1547f, 695f, 1863f), 155f),
                ),
            arrows =
                listOf(
                    GuideArrow(Offset(330f, 285f), Offset(354f, 230f), Offset(505f, 194f)),
                    GuideArrow(Offset(937f, 565f), Offset(1040f, 478f), Offset(982f, 365f)),
                    GuideArrow(Offset(330f, 1430f), Offset(365f, 1510f), Offset(503f, 1533f)),
                ),
            annotations =
                listOf(
                    GuideAnnotation(
                        Res.string.guide_map_list_title,
                        Res.string.guide_map_list_accent,
                        Res.string.guide_map_list_body,
                        Offset(.10f, .142f),
                        .75f,
                    ),
                    GuideAnnotation(
                        Res.string.guide_map_filter_title,
                        Res.string.guide_map_filter_accent,
                        Res.string.guide_map_filter_body,
                        Offset(.45f, .281f),
                        .49f,
                    ),
                    GuideAnnotation(
                        Res.string.guide_map_record_title,
                        Res.string.guide_map_record_accent,
                        Res.string.guide_map_record_body,
                        Offset(.10f, .62f),
                        .80f,
                    ),
                ),
            buttonTop = .44f,
        ),
        FeatureGuidePage(
            screen = Res.drawable.onboarding_guide_press,
            description = Res.string.onboarding_guide_press_description,
            highlights =
                listOf(
                    GuideHighlight(Rect(670f, 280f, 1030f, 364f), 26f),
                    GuideHighlight(Rect(95f, 520f, 985f, 626f), 38f),
                    GuideHighlight(Rect(262f, 973f, 526f, 1251f), 132f),
                ),
            arrows =
                listOf(
                    GuideArrow(Offset(680f, 195f), Offset(786f, 194f), Offset(822f, 258f)),
                    GuideArrow(Offset(385f, 685f), Offset(463f, 695f), Offset(516f, 645f)),
                    GuideArrow(Offset(559f, 1342f), Offset(563f, 1282f), Offset(489f, 1260f)),
                ),
            annotations =
                listOf(
                    GuideAnnotation(
                        Res.string.guide_press_ranking_title,
                        Res.string.guide_press_ranking_accent,
                        Res.string.guide_press_ranking_body,
                        Offset(.10f, .05f),
                        .78f,
                    ),
                    GuideAnnotation(
                        Res.string.guide_press_mine_title,
                        Res.string.guide_press_mine_accent,
                        Res.string.guide_press_mine_body,
                        Offset(.10f, .332f),
                        .78f,
                    ),
                    GuideAnnotation(
                        Res.string.guide_press_emotion_title,
                        Res.string.guide_press_emotion_accent,
                        Res.string.guide_press_emotion_body,
                        Offset(.10f, .663f),
                        .80f,
                        centered = true,
                    ),
                ),
            buttonTop = .84f,
        ),
        FeatureGuidePage(
            screen = Res.drawable.onboarding_collection,
            description = Res.string.onboarding_guide_collection_description,
            highlights =
                listOf(
                    GuideHighlight(Rect(768f, 70f, 1018f, 192f), 61f),
                    GuideHighlight(Rect(129f, 513f, 951f, 1603f), 50f),
                    GuideHighlight(Rect(180f, 1360f, 900f, 1570f), 40f, cutout = false),
                ),
            arrows =
                listOf(
                    GuideArrow(Offset(636f, 150f), Offset(681f, 111f), Offset(744f, 129f)),
                    GuideArrow(Offset(349f, 455f), Offset(378f, 483f), Offset(450f, 493f)),
                    GuideArrow(Offset(785f, 1632f), Offset(825f, 1610f), Offset(806f, 1586f)),
                ),
            annotations =
                listOf(
                    GuideAnnotation(
                        Res.string.guide_collection_mode_title,
                        Res.string.guide_collection_mode_accent,
                        Res.string.guide_collection_mode_body,
                        Offset(.09f, .047f),
                        .65f,
                    ),
                    GuideAnnotation(
                        Res.string.guide_collection_card_title,
                        Res.string.guide_collection_card_accent,
                        Res.string.guide_collection_card_body,
                        Offset(.09f, .15f),
                        .82f,
                    ),
                    GuideAnnotation(
                        Res.string.guide_collection_stats_title,
                        Res.string.guide_collection_stats_accent,
                        null,
                        Offset(.10f, .76f),
                        .80f,
                        centered = true,
                    ),
                ),
            buttonTop = 0f,
            collection = true,
        ),
    )

/** Three informational overviews. Starting leaves this composition and opens the normal map route. */
@Composable
internal fun FeatureGuideScreen(
    state: FeatureGuideState,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = state.page) { FEATURE_GUIDE_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) state.showPage(pagerState.settledPage)
    }
    FlowBackHandler(enabled = pagerState.settledPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.settledPage - 1) }
    }
    Box(modifier.fillMaxSize().background(AppColors.Surface).safeDrawingPadding()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF102017))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val page = featureGuidePages[pageIndex]
                    val sourceHeight = if (page.collection) 2130f else 2070f
                    val width = minOf(maxWidth, maxHeight * (1080f / sourceHeight))
                    val height = width * (sourceHeight / 1080f)
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .width(width)
                            .height(height)
                            .clipToBounds(),
                    ) {
                        OnboardingAppCapture(
                            page.screen,
                            stringResource(page.description),
                            Modifier.fillMaxSize(),
                            page.collection,
                        )
                        FeatureGuideAnnotations(page, pageIndex, enabled = !pagerState.isScrollInProgress) {
                            if (pagerState.settledPage == pageIndex && !pagerState.isScrollInProgress) {
                                if (pageIndex == FEATURE_GUIDE_PAGE_COUNT - 1) {
                                    state.showPage(pageIndex)
                                    state.finish()
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                                }
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 4.dp)
                                .size(76.dp, 44.dp)
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = state::skip,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(Res.string.onboarding_skip), fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureGuideAnnotations(
    page: FeatureGuidePage,
    index: Int,
    enabled: Boolean,
    onNext: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }) {
        val width = maxWidth
        val height = maxHeight
        val textScale = (width / 390.dp).coerceAtMost(1.25f)
        val sourceHeight = if (page.collection) 2130f else 2070f
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 1080f
            val sy = size.height / sourceHeight

            fun Offset.scaled() = Offset(x * sx, y * sy)

            fun Rect.scaled() = Rect(left * sx, top * sy, right * sx, bottom * sy)
            val cutout =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    page.highlights.filter { it.cutout }.forEach {
                        addRoundRect(RoundRect(it.bounds.scaled(), CornerRadius(it.radius * sx, it.radius * sy)))
                    }
                }
            drawPath(cutout, Color(0xD6102017))
            page.highlights.forEach {
                val bounds = it.bounds.scaled()
                drawRoundRect(
                    AppColors.Primary,
                    bounds.topLeft,
                    bounds.size,
                    CornerRadius(it.radius * sx, it.radius * sy),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            page.arrows.forEach {
                val start = it.start.scaled()
                val control = it.control.scaled()
                val end = it.end.scaled()
                val path =
                    Path().apply {
                        moveTo(start.x, start.y)
                        quadraticTo(control.x, control.y, end.x, end.y)
                    }
                drawPath(path, AppColors.Primary, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                val angle = atan2(end.y - control.y, end.x - control.x)
                for (delta in listOf(-.55f, .55f)) {
                    val tip = end - Offset(cos(angle + delta), sin(angle + delta)) * 6.dp.toPx()
                    drawLine(AppColors.Primary, tip, end, 1.8.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        page.annotations.forEach { annotation ->
            val title = stringResource(annotation.title)
            val accent = stringResource(annotation.accent)
            val titleText =
                buildAnnotatedString {
                    append(title)
                    val accentIndex = title.indexOf(accent)
                    if (accentIndex >=
                        0
                    ) {
                        addStyle(SpanStyle(color = AppColors.Primary), accentIndex, accentIndex + accent.length)
                    }
                }
            Column(
                Modifier
                    .offset(x = width * annotation.position.x, y = height * annotation.position.y)
                    .width(width * annotation.width),
                horizontalAlignment = if (annotation.centered) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    titleText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (17f * textScale).coerceAtLeast(14f).sp,
                    lineHeight = (25f * textScale).coerceAtLeast(21f).sp,
                    textAlign = if (annotation.centered) TextAlign.Center else TextAlign.Start,
                )
                annotation.body?.let {
                    Text(
                        stringResource(it),
                        color = Color(0xFFF0F4E9),
                        fontSize = (13f * textScale).coerceAtLeast(11f).sp,
                        lineHeight = (22f * textScale).coerceAtLeast(18f).sp,
                        textAlign = if (annotation.centered) TextAlign.Center else TextAlign.Start,
                    )
                }
            }
        }
        val actionTop = if (page.collection) height * .895f - 56.dp else height * page.buttonTop
        val actionModifier =
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = actionTop)
                .width(width * .66f)
                .height(48.dp)
        if (page.collection) {
            Box(
                actionModifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(AppColors.Primary)
                    .clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = onNext,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.onboarding_start),
                    color = AppColors.GroupInk,
                    fontSize = (14f * textScale).coerceAtLeast(12f).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Column(
                actionModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("${index + 1} / $FEATURE_GUIDE_PAGE_COUNT", fontSize = 10.sp, color = AppColors.Primary)
                Text(
                    stringResource(Res.string.onboarding_guide_swipe_hint),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Preview(name = "지도 기능 안내", widthDp = 390, heightDp = 844)
@Composable
private fun MapFeatureGuidePreview() = FeatureGuideScreen(FeatureGuideState(true) {})

@Preview(name = "프레스 기능 안내", widthDp = 390, heightDp = 844)
@Composable
private fun PressFeatureGuidePreview() = FeatureGuideScreen(FeatureGuideState(true) {}.apply { next() })

@Preview(name = "컬렉션 기능 안내", widthDp = 390, heightDp = 844)
@Composable
private fun CollectionFeatureGuidePreview() =
    FeatureGuideScreen(
        FeatureGuideState(true) {}.apply {
            next()
            next()
        },
    )
