package com.pheeeew.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.map.star.MapPinStar

@Composable
internal fun Onboarding2() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NameExamples()
        Spacer(modifier = Modifier.height(40.dp))
        OnboardingTitle(
            text = "이름은 한숨마다\n새로 붙어요",
            textAlign = TextAlign.Center,
            fontSize = 26.sp,
            lineHeight = 33.sp,
            letterSpacing = 0.sp,
        )
        Spacer(modifier = Modifier.height(50.dp))

        SupportingText(
            text = "한숨을 쉬면\n 새로운 이름이 생성됩니다.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun NameExamples(modifier: Modifier = Modifier) {
    Column(modifier = modifier.height(200.dp)) {
        NameExample(
            name = "꿈꾸러기 수달",
            color = AppColors.StarWarm,
            starSize = 27,
            modifier =
                Modifier
                    .width(300.dp),
        )
        NameExample(
            name = "노래하는 고라니",
            color = AppColors.StarFresh,
            starSize = 32,
            highlighted = true,
            modifier =
                Modifier
                    .width(330.dp),
        )
        NameExample(
            name = "느린 나무늘보",
            color = AppColors.StarDeep,
            starSize = 27,
            modifier =
                Modifier
                    .width(300.dp),
        )
    }
}

@Composable
private fun NameExample(
    name: String,
    color: Color,
    starSize: Int,
    highlighted: Boolean = false,
    trailingText: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .then(
                    if (highlighted) {
                        Modifier
                            .background(AppColors.Navy700.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(1.dp, AppTheme.colors.outline.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        MapPinStar(color = color, modifier = Modifier.size(starSize.dp))
        Text(
            text = name,
            color = AppColors.Blue100.copy(alpha = 0.5f),
            fontFamily = FontFamily.Default,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 13.dp),
        )
        Spacer(Modifier.weight(1f))
        trailingText?.let {
            Text(
                text = it,
                color = AppColors.Blue100.copy(alpha = 0.35f),
                fontFamily = FontFamily.Default,
                fontSize = 10.sp,
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun Onboarding2Preview() {
    OnboardingPagePreview(page = 1) {
        Onboarding2()
    }
}
