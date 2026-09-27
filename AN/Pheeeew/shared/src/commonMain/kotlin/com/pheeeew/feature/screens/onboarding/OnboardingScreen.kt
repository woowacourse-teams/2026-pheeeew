package com.pheeeew.feature.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated

const val WELCOME_ONBOARDING_COMPLETED_KEY = "onboarding_completed"

private data class OnboardingPage(
    val message: String,
    val illustration: DrawableResource,
    val supportingText: String? = null,
)

private val onboardingPages =
    listOf(
        OnboardingPage(
            message = "오늘도 왜 나만\n이렇게 힘든 거죠?",
            illustration = Res.drawable.ic_emotion_discouraged,
        ),
        OnboardingPage(
            message = "괜찮은 척하기도\n이제 지쳤어요.",
            illustration = Res.drawable.ic_emotion_exhausted,
        ),
        OnboardingPage(
            message = "참으라고요?\n내가 제일 힘든데요.",
            illustration = Res.drawable.ic_emotion_frustrated,
        ),
        OnboardingPage(
            message = "남들도 힘들다지만\n일단 내가 제일 힘들어요!!",
            illustration = Res.drawable.ic_emotion_irritated,
        ),
        OnboardingPage(
            message = "내가 제일 힘들다는 걸\n이 공간에서 표출해봐요!!",
            illustration = Res.drawable.ic_emotion_angry,
            supportingText = "참았던 마음을 지도에 뿜어봐요\n매일 50자, 목소리 30초\n아무것도 없이 남겨도 좋아요",
        ),
    )

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFFFFFEFE))
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "히유",
                color = Color(0xFF202323),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "건너뛰기",
                color = Color(0xFF777777),
                fontSize = 11.sp,
                modifier = Modifier.clickable(role = Role.Button, onClick = onFinished),
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val item = onboardingPages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Spacer(Modifier.fillMaxHeight(0.25f))
                Image(
                    painter = painterResource(item.illustration),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(170.dp),
                )
                Spacer(Modifier.height(76.dp))
                Text(
                    text = item.message,
                    color = Color(0xFF202323),
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                item.supportingText?.let { supportingText ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = supportingText,
                        color = Color(0xFF777777),
                        fontSize = 12.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onboardingPages.indices.forEach { index ->
                Box(
                    modifier =
                        Modifier
                            .size(if (index == pagerState.currentPage) 6.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) Color(0xFF202323) else Color(0xFFD8D8D8)),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF202323))
                    .clickable(role = Role.Button) {
                        if (isLastPage) {
                            onFinished()
                        } else {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isLastPage) "내 감정 뿜으로 가기" else "다음",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Preview(name = "Onboarding 1", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingPage1Preview() {
    OnboardingScreen(onFinished = {}, initialPage = 0)
}

@Preview(name = "Onboarding 2", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingPage2Preview() {
    OnboardingScreen(onFinished = {}, initialPage = 1)
}

@Preview(name = "Onboarding 3", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingPage3Preview() {
    OnboardingScreen(onFinished = {}, initialPage = 2)
}

@Preview(name = "Onboarding 4", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingPage4Preview() {
    OnboardingScreen(onFinished = {}, initialPage = 3)
}

@Preview(name = "Onboarding 5", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingPage5Preview() {
    OnboardingScreen(onFinished = {}, initialPage = 4)
}
