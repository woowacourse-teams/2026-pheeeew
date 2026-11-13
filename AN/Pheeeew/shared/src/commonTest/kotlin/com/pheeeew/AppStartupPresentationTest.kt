package com.pheeeew

import kotlin.test.Test
import kotlin.test.assertEquals

class AppStartupPresentationTest {
    @Test
    fun `기존 사용자는 버전 확인 중에도 메인 지도를 준비한다`() {
        assertEquals(
            AppStartupPresentation.MAIN,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = false,
                splashDismissed = true,
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
                splashDismissed = false,
                requiredUpdate = true,
                onboardingCompleted = true,
            ),
        )
        assertEquals(
            AppStartupPresentation.SPLASH,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = true,
                splashDismissed = false,
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
                splashDismissed = true,
                requiredUpdate = true,
                onboardingCompleted = true,
            ),
        )
        assertEquals(
            AppStartupPresentation.ONBOARDING,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = true,
                splashDismissed = true,
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
                splashDismissed = false,
                requiredUpdate = false,
                onboardingCompleted = true,
            ),
        )
    }

    @Test
    fun `버전 확인이 늦은 신규 사용자도 최대 시간 이후 splash를 닫는다`() {
        assertEquals(
            AppStartupPresentation.VERSION_CHECK,
            resolveAppStartupPresentation(
                initialVersionCheckComplete = false,
                splashDismissed = true,
                requiredUpdate = false,
                onboardingCompleted = false,
            ),
        )
    }

    @Test
    fun `온보딩을 완료하면 바로 메인 진입을 준비한다`() {
        assertEquals(
            AppStartupPresentation.MAIN,
            resolveAppStartupPresentation(true, true, false, true),
        )
        assertEquals(
            AppStartupPresentation.REQUIRED_UPDATE,
            resolveAppStartupPresentation(true, true, true, true),
        )
    }
}
