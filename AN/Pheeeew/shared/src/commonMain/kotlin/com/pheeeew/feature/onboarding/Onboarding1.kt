package com.pheeeew.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun Onboarding1() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingTitle(
            text = "내쉰 한숨은\n내가 서 있던 자리에\n별이 되어 남습니다",
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(50.dp))

        SupportingText(
            text = "누구인지는 아무도 몰라요.\n대략적인 위치와, 남긴 한숨만 보입니다.",
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(130.dp))
    }
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun Onboarding1Preview() {
    OnboardingPagePreview(page = 0) {
        Onboarding1()
    }
}
