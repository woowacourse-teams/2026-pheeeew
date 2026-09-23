package com.pheeeew.legacy.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.legacy.core.designsystem.theme.AppColors
import com.pheeeew.legacy.feature.map.star.MapPinStar

@Composable
internal fun Onboarding3() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        ThirdPageMapPins()
        Spacer(modifier = Modifier.height(40.dp))

        OnboardingTitle(
            text = "나의 한숨이 모여\n하늘을 비춰요.",
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(50.dp))
        SupportingText(
            text = "별이 된 한숨을 위로해봐요",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun ThirdPageMapPins() {
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        MapPinStar(
            color = AppColors.StarFresh,
            modifier = Modifier.size(44.dp).align(Alignment.TopCenter),
        )
        MapPinStar(
            color = AppColors.StarWarm,
            modifier = Modifier.size(102.dp).align(Alignment.CenterStart).padding(start = 60.dp),
        )

        MapPinStar(
            color = AppColors.StarDeep,
            modifier = Modifier.size(85.dp).align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 45.dp),
        )
        MapPinStar(
            color = AppColors.Cream100,
            modifier = Modifier.size(28.dp).align(Alignment.BottomCenter),
        )
    }
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun Onboarding3Preview() {
    OnboardingPagePreview(page = 2) {
        Onboarding3()
    }
}
