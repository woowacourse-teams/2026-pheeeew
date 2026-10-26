package com.pheeeew.feature.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.ProductScreen
import com.pheeeew.feature.monitoring.product.labels
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.onboarding_brand
import pheeeew.shared.generated.resources.onboarding_collection
import pheeeew.shared.generated.resources.onboarding_emotion
import pheeeew.shared.generated.resources.onboarding_lookaround
import pheeeew.shared.generated.resources.onboarding_memo
import pheeeew.shared.generated.resources.onboarding_next
import pheeeew.shared.generated.resources.onboarding_press
import pheeeew.shared.generated.resources.onboarding_skip
import pheeeew.shared.generated.resources.onboarding_slide_collection_body
import pheeeew.shared.generated.resources.onboarding_slide_collection_title
import pheeeew.shared.generated.resources.onboarding_slide_emotion_body
import pheeeew.shared.generated.resources.onboarding_slide_emotion_title
import pheeeew.shared.generated.resources.onboarding_slide_memo_body
import pheeeew.shared.generated.resources.onboarding_slide_memo_title
import pheeeew.shared.generated.resources.onboarding_slide_press_body
import pheeeew.shared.generated.resources.onboarding_slide_press_title

const val WELCOME_ONBOARDING_COMPLETED_KEY = "onboarding_completed"
private val OnboardingBackground = Color(0xFFFCFCFA)

private data class OnboardingPage(
    val title: StringResource,
    val body: StringResource,
    val screen: DrawableResource,
)

private val onboardingPages =
    listOf(
        OnboardingPage(
            Res.string.onboarding_slide_emotion_title,
            Res.string.onboarding_slide_emotion_body,
            Res.drawable.onboarding_emotion,
        ),
        OnboardingPage(
            Res.string.onboarding_slide_memo_title,
            Res.string.onboarding_slide_memo_body,
            Res.drawable.onboarding_memo,
        ),
        OnboardingPage(
            Res.string.onboarding_slide_press_title,
            Res.string.onboarding_slide_press_body,
            Res.drawable.onboarding_press,
        ),
        OnboardingPage(
            Res.string.onboarding_slide_collection_title,
            Res.string.onboarding_slide_collection_body,
            Res.drawable.onboarding_collection,
        ),
    )

@Composable
fun OnboardingScreen(
    onFinished: suspend () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    monitoring: com.pheeeew.core.monitoring.Monitoring = com.pheeeew.core.monitoring.NoOpMonitoring,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { onboardingPages.size })
    val telemetry = remember(monitoring) { ProductMonitoring(monitoring, "onboarding") }
    ProductScreen(telemetry)
    val foreground =
        com.pheeeew.feature.screens.map.monitoring
            .rememberMonitoringForeground()
    LaunchedEffect(pagerState.settledPage, foreground) {
        if (foreground) {
            androidx.compose.runtime.withFrameNanos { }
            telemetry.emit(
                "onboarding_step_viewed",
                mapOf(
                    "page_index" to
                        com.pheeeew.core.monitoring.EventValue
                            .Integer(pagerState.settledPage.toLong()),
                ),
            )
        }
    }
    var completed by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun finish(outcome: String) {
        if (completed || isFinishing) return
        isFinishing = true
        scope.launch {
            try {
                onFinished()
                telemetry.emit("onboarding_finished", labels("outcome" to outcome))
                completed = true
            } finally {
                isFinishing = false
            }
        }
    }
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex
    Column(modifier.fillMaxSize().background(OnboardingBackground).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(Res.string.onboarding_brand),
                color = AppColors.GroupInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.onboarding_skip),
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                modifier =
                    Modifier
                        .clickable(enabled = !isFinishing, role = Role.Button) { finish("skipped") }
                        .padding(vertical = 14.dp),
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = onboardingPages[index]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(page.title),
                    color = AppColors.GroupInk,
                    fontSize = 24.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(page.body),
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
                OnboardingPhone(page.screen, stringResource(page.title), Modifier.weight(1f).fillMaxWidth(), index == 3)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onboardingPages.indices.forEach { index ->
                Box(
                    Modifier.size(30.dp, 40.dp).clickable(role = Role.Button) {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (index == pagerState.currentPage) 19.dp else 5.dp, 5.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) AppColors.GroupInk else Color(0xFFD1D7C9)),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Primary)
                .clickable(enabled = !isFinishing && !pagerState.isScrollInProgress, role = Role.Button) {
                    if (isLastPage) {
                        finish("completed")
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (isLastPage) Res.string.onboarding_lookaround else Res.string.onboarding_next),
                color = AppColors.GroupInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Keep the reviewed app capture intact; only OS bars were trimmed from the bundled artwork. */
@Composable
private fun OnboardingPhone(
    screen: DrawableResource,
    description: String,
    modifier: Modifier,
    collection: Boolean,
) {
    BoxWithConstraints(
        modifier.clipToBounds().drawWithContent {
            drawContent()
            val fade = 70.dp.toPx().coerceAtMost(size.height * 0.22f)
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, OnboardingBackground.copy(alpha = 0.65f), OnboardingBackground),
                    startY = size.height - fade,
                    endY = size.height,
                ),
            )
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        val ratio = (if (collection) 2130f else 2070f) / 1080f
        val width = minOf(252.dp, maxWidth * 0.7f, ((maxHeight - 38.dp).coerceAtLeast(0.dp) / ratio) + 14.dp)
        Column(
            Modifier
                .width(width)
                .shadow(7.dp, RoundedCornerShape(32.dp))
                .background(Color(0xFFE7ECE3), RoundedCornerShape(32.dp))
                .border(1.dp, Color(0xFFD5DCD0), RoundedCornerShape(32.dp))
                .padding(7.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(AppColors.Surface),
        ) {
            Box(Modifier.fillMaxWidth().height(20.dp).background(Color(0xFFF9FBF7))) {
                Text(
                    "9:41",
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 5.dp),
                )
                Box(Modifier.align(Alignment.Center).size(5.dp).background(AppColors.GroupInk, CircleShape))
            }
            OnboardingAppCapture(screen, description, Modifier.fillMaxWidth(), collection)
        }
    }
}

@Preview(name = "Onboarding · 감정 선택", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingEmotionPreview() = OnboardingScreen(onFinished = {}, initialPage = 0)

@Preview(name = "Onboarding · 마음 기록", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingMemoPreview() = OnboardingScreen(onFinished = {}, initialPage = 1)

@Preview(name = "Onboarding · 프레스", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingPressPreview() = OnboardingScreen(onFinished = {}, initialPage = 2)

@Preview(name = "Onboarding · 컬렉션", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingCollectionPreview() = OnboardingScreen(onFinished = {}, initialPage = 3)
