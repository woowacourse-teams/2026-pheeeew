package com.pheeeew

import kotlin.test.Test
import kotlin.test.assertEquals

class AppStartupPresentationTest {
    @Test
    fun `버전 확인 전에는 메인 화면을 준비하지 않는다`() {
        assertEquals(
            AppStartupPresentation.SPLASH,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = false,
                splashAnimationCompleted = true,
                requiredUpdate = false,
                onboardingCompleted = true,
            ),
        )
    }

    @Test
    fun `스플래시가 끝나기 전에는 필수 업데이트와 온보딩을 표시하지 않는다`() {
        assertEquals(
            AppStartupPresentation.SPLASH,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = true,
                splashAnimationCompleted = false,
                requiredUpdate = true,
                onboardingCompleted = true,
            ),
        )
        assertEquals(
            AppStartupPresentation.SPLASH,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = true,
                splashAnimationCompleted = false,
                requiredUpdate = false,
                onboardingCompleted = false,
            ),
        )
    }

    @Test
    fun `스플래시 종료 후 필수 업데이트와 온보딩을 메인보다 먼저 표시한다`() {
        assertEquals(
            AppStartupPresentation.REQUIRED_UPDATE,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = true,
                splashAnimationCompleted = true,
                requiredUpdate = true,
                onboardingCompleted = true,
            ),
        )
        assertEquals(
            AppStartupPresentation.ONBOARDING,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = true,
                splashAnimationCompleted = true,
                requiredUpdate = false,
                onboardingCompleted = false,
            ),
        )
    }

    @Test
    fun `초기 준비가 끝나면 스플래시 애니메이션 중에도 메인을 지도 뒤에서 준비한다`() {
        assertEquals(
            AppStartupPresentation.MAIN,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = true,
                splashAnimationCompleted = false,
                requiredUpdate = false,
                onboardingCompleted = true,
            ),
        )
    }
}
