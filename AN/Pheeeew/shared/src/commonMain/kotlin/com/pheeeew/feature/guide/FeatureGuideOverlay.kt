package com.pheeeew.feature.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pheeeew.core.designsystem.component.RaisedCardButton
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.core.navigation.FlowBackHandler
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.coachmark_calendar_day_description
import pheeeew.shared.generated.resources.coachmark_calendar_day_title
import pheeeew.shared.generated.resources.coachmark_calendar_month_description
import pheeeew.shared.generated.resources.coachmark_calendar_month_title
import pheeeew.shared.generated.resources.coachmark_collection_calendar_description
import pheeeew.shared.generated.resources.coachmark_collection_calendar_title
import pheeeew.shared.generated.resources.coachmark_collection_claim_description
import pheeeew.shared.generated.resources.coachmark_collection_claim_title
import pheeeew.shared.generated.resources.coachmark_collection_condition_description
import pheeeew.shared.generated.resources.coachmark_collection_condition_title
import pheeeew.shared.generated.resources.coachmark_collection_share_description
import pheeeew.shared.generated.resources.coachmark_collection_share_title
import pheeeew.shared.generated.resources.coachmark_map_filter_description
import pheeeew.shared.generated.resources.coachmark_map_filter_title
import pheeeew.shared.generated.resources.coachmark_map_list_description
import pheeeew.shared.generated.resources.coachmark_map_list_title
import pheeeew.shared.generated.resources.coachmark_map_record_description
import pheeeew.shared.generated.resources.coachmark_map_record_title
import pheeeew.shared.generated.resources.coachmark_next
import pheeeew.shared.generated.resources.coachmark_press_activity_description
import pheeeew.shared.generated.resources.coachmark_press_activity_title
import pheeeew.shared.generated.resources.coachmark_press_rank_description
import pheeeew.shared.generated.resources.coachmark_press_rank_title
import pheeeew.shared.generated.resources.coachmark_press_tap_description
import pheeeew.shared.generated.resources.coachmark_press_tap_title
import pheeeew.shared.generated.resources.coachmark_skip
import pheeeew.shared.generated.resources.coachmark_start

internal val LocalFeatureGuideAnchorReporter =
    staticCompositionLocalOf<((String, Int, Rect?) -> Unit)?> { null }

internal const val COLLECTION_ISSUED_GUIDE_ANCHOR = 3
internal const val COLLECTION_CALENDAR_GUIDE_ANCHOR = 2

private val collectionCalendarGuidePage =
    FeatureGuidePage(
        Res.string.coachmark_collection_calendar_title,
        Res.string.coachmark_collection_calendar_description,
        anchorStep = COLLECTION_CALENDAR_GUIDE_ANCHOR,
    )

@Composable
internal fun Modifier.reportFeatureGuideAnchor(
    guideId: String,
    step: Int,
): Modifier {
    val reporter = LocalFeatureGuideAnchorReporter.current ?: return this
    DisposableEffect(reporter, guideId, step) {
        onDispose { reporter(guideId, step, null) }
    }
    return onGloballyPositioned { coordinates ->
        reporter(guideId, step, coordinates.boundsInRoot())
    }
}

internal enum class FeatureGuide(
    val storageId: String,
    val pages: List<FeatureGuidePage>,
) {
    Map(
        "map",
        listOf(
            FeatureGuidePage(Res.string.coachmark_map_record_title, Res.string.coachmark_map_record_description),
            FeatureGuidePage(
                Res.string.coachmark_map_list_title,
                Res.string.coachmark_map_list_description,
                spotlightShape = FeatureGuideSpotlightShape.Pill,
            ),
            FeatureGuidePage(Res.string.coachmark_map_filter_title, Res.string.coachmark_map_filter_description),
        ),
    ),
    Press(
        "press",
        listOf(
            FeatureGuidePage(Res.string.coachmark_press_tap_title, Res.string.coachmark_press_tap_description),
            FeatureGuidePage(
                Res.string.coachmark_press_activity_title,
                Res.string.coachmark_press_activity_description,
            ),
            FeatureGuidePage(Res.string.coachmark_press_rank_title, Res.string.coachmark_press_rank_description),
        ),
    ),
    Collection(
        "collection",
        listOf(
            FeatureGuidePage(
                Res.string.coachmark_collection_condition_title,
                Res.string.coachmark_collection_condition_description,
            ),
            FeatureGuidePage(
                Res.string.coachmark_collection_claim_title,
                Res.string.coachmark_collection_claim_description,
            ),
            collectionCalendarGuidePage,
        ),
    ),
    CollectionIssued(
        "collection",
        listOf(
            FeatureGuidePage(
                Res.string.coachmark_collection_share_title,
                Res.string.coachmark_collection_share_description,
                anchorStep = COLLECTION_ISSUED_GUIDE_ANCHOR,
            ),
            collectionCalendarGuidePage,
        ),
    ),
    Calendar(
        "calendar",
        listOf(
            FeatureGuidePage(
                Res.string.coachmark_calendar_month_title,
                Res.string.coachmark_calendar_month_description,
            ),
            FeatureGuidePage(Res.string.coachmark_calendar_day_title, Res.string.coachmark_calendar_day_description),
        ),
    ),
}

internal data class FeatureGuidePage(
    val title: StringResource,
    val description: StringResource,
    val spotlightShape: FeatureGuideSpotlightShape = FeatureGuideSpotlightShape.RoundedRectangle,
    val anchorStep: Int? = null,
)

internal enum class FeatureGuideSpotlightShape {
    RoundedRectangle,
    Pill,
}

internal data class FeatureGuideSession(
    val guide: FeatureGuide,
    val page: Int = 0,
) {
    val anchorKey: Pair<String, Int>
        get() = guide.storageId to (guide.pages[page].anchorStep ?: page)
}

@Composable
internal fun FeatureGuideOverlay(
    session: FeatureGuideSession,
    anchorBounds: Rect,
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density)

    FlowBackHandler(enabled = true, onBack = {})
    Box(modifier.fillMaxSize().zIndex(10_000f)) {
        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val panelWidth = (width - 40.dp.roundToPx()).coerceIn(0, 320.dp.roundToPx())
            val panel =
                subcompose("panel") {
                    FeatureGuidePanel(session, onAdvance)
                }.single().measure(
                    constraints.copy(minWidth = panelWidth, maxWidth = panelWidth, minHeight = 0),
                )
            val topInset = statusBarHeight + 16.dp.toPx()
            val bottomInset = navigationBarHeight + 16.dp.toPx()
            val pointerGap = 14.dp.toPx()
            val aboveTop = anchorBounds.top - panel.height - pointerGap
            val preferredTop =
                if (anchorBounds.center.y > height * 0.58f && aboveTop >= topInset) {
                    aboveTop
                } else {
                    anchorBounds.bottom + pointerGap
                }
            val panelTop =
                preferredTop
                    .coerceIn(topInset, (height - bottomInset - panel.height).coerceAtLeast(topInset))
                    .toInt()
            val panelLeft = (width - panel.width) / 2
            val panelBounds =
                Rect(
                    left = panelLeft.toFloat(),
                    top = panelTop.toFloat(),
                    right = (panelLeft + panel.width).toFloat(),
                    bottom = (panelTop + panel.height).toFloat(),
                )
            val backdrop =
                subcompose("backdrop") {
                    FeatureGuideBackdrop(session, anchorBounds, panelBounds, onAdvance)
                }.single().measure(constraints)

            layout(width, height) {
                backdrop.place(0, 0)
                panel.place(panelLeft, panelTop)
            }
        }

        Text(
            text = stringResource(Res.string.coachmark_skip),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = with(density) { statusBarHeight.toDp() } + 16.dp, end = 16.dp)
                    .background(Color.Transparent, RoundedCornerShape(16.dp))
                    .semantics { role = Role.Button }
                    .clickable(onClick = onSkip, role = Role.Button)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            color = Color.White,
            fontFamily = notoSansKrFontFamily(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FeatureGuideBackdrop(
    session: FeatureGuideSession,
    anchorBounds: Rect,
    panelBounds: Rect,
    onAdvance: () -> Unit,
) {
    val spotlightShape = session.guide.pages[session.page].spotlightShape
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(session, onAdvance) {
                detectTapGestures(onTap = { onAdvance() })
            }.semantics {
                contentDescription = "가이드 다음 단계"
                role = Role.Button
                onClick(label = "다음 단계") {
                    onAdvance()
                    true
                }
            },
    ) {
        val targetPadding = 8.dp.toPx()
        val target =
            Rect(
                left = (anchorBounds.left - targetPadding).coerceIn(0f, size.width),
                top = (anchorBounds.top - targetPadding).coerceIn(0f, size.height),
                right = (anchorBounds.right + targetPadding).coerceIn(0f, size.width),
                bottom = (anchorBounds.bottom + targetPadding).coerceIn(0f, size.height),
            )
        val targetSize = Size(target.width, target.height)
        val targetTopLeft = Offset(target.left, target.top)
        val cornerRadius =
            CornerRadius(
                when (spotlightShape) {
                    FeatureGuideSpotlightShape.RoundedRectangle -> 18.dp.toPx()
                    FeatureGuideSpotlightShape.Pill -> target.height / 2f
                },
            )
        val dim =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(RoundRect(target, cornerRadius))
            }
        drawPath(dim, Color(0xA6000000))
        drawRoundRect(
            color = AppColors.Primary.copy(alpha = 0.45f),
            topLeft = targetTopLeft,
            size = targetSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 8.dp.toPx()),
        )
        drawRoundRect(
            color = AppColors.Primary,
            topLeft = targetTopLeft,
            size = targetSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 3.dp.toPx()),
        )
        val arrowPadding = 20.dp.toPx().coerceAtMost(panelBounds.width / 2f)
        val pointX = anchorBounds.center.x.coerceIn(panelBounds.left + arrowPadding, panelBounds.right - arrowPadding)
        val panelAboveTarget = panelBounds.bottom <= target.top
        val baseY = if (panelAboveTarget) panelBounds.bottom else panelBounds.top
        val tipY = if (panelAboveTarget) target.top else target.bottom
        val triangle =
            Path().apply {
                moveTo(pointX, tipY)
                lineTo(pointX - 11.dp.toPx(), baseY)
                lineTo(pointX + 11.dp.toPx(), baseY)
                close()
            }
        drawPath(triangle, Color.White)
        drawPath(triangle, AppColors.GroupInk, style = Stroke(width = 1.5.dp.toPx()))
    }
}

@Composable
private fun FeatureGuidePanel(
    session: FeatureGuideSession,
    onAdvance: () -> Unit,
) {
    val page = session.guide.pages[session.page]
    Column(
        modifier =
            Modifier
                .shadow(4.dp, RoundedCornerShape(18.dp))
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.5.dp, AppColors.GroupInk, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(page.title),
            color = AppColors.GroupInk,
            fontFamily = notoSansKrFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(page.description),
            color = AppColors.TextSecondary,
            fontFamily = notoSansKrFontFamily(),
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${session.page + 1}/${session.guide.pages.size}",
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            RaisedCardButton(
                backgroundColor = AppColors.Primary,
                onClick = onAdvance,
                modifier = Modifier.height(34.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (session.page == session.guide.pages.lastIndex) {
                                Res.string.coachmark_start
                            } else {
                                Res.string.coachmark_next
                            },
                        ),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                    color = AppColors.GroupInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Preview(name = "화면 가이드", widthDp = 390, heightDp = 844)
@Composable
private fun FeatureGuideOverlayPreview() {
    AppTheme {
        val density = LocalDensity.current
        val previewTarget = with(density) { Rect(115.dp.toPx(), 620.dp.toPx(), 275.dp.toPx(), 700.dp.toPx()) }
        Box(Modifier.fillMaxSize().background(AppColors.GroupBackground)) {
            FeatureGuideOverlay(
                session = FeatureGuideSession(FeatureGuide.Map),
                anchorBounds = previewTarget,
                onAdvance = {},
                onSkip = {},
            )
        }
    }
}
